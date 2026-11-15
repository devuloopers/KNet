import contextlib
from email.message import Message
import http.client
from http.server import BaseHTTPRequestHandler, HTTPServer
import io
import json
import threading
import unittest
import urllib.error
import urllib.parse
import urllib.request
from unittest.mock import MagicMock, Mock, patch

from coolify import DeploymentTarget, MAX_RESPONSE_BYTES, RejectionReason, RejectRedirects, main, request_deployment

WEBHOOK = 'https://coolify.example.test/api/v1/deploy?uuid=test-website&force=false'
TOKEN = 'synthetic-ci-deploy-token'


class CoolifyDeploymentTest(unittest.TestCase):
    def target(self, webhook=WEBHOOK):
        return DeploymentTarget.from_environment({'COOLIFY_WEBHOOK': webhook, 'COOLIFY_TOKEN': TOKEN})

    def response(self, body=None, status=200):
        opener = MagicMock()
        response = opener.open.return_value.__enter__.return_value
        response.status = status
        response.read.return_value = body if body is not None else json.dumps({
            'deployments': [{'resource_uuid': 'test-website', 'deployment_uuid': 'queued-test-deployment'}],
        }).encode()
        return opener

    def test_missing_or_malformed_configuration_rejects_before_network_access(self):
        for values in ({}, {'COOLIFY_WEBHOOK': WEBHOOK}, {'COOLIFY_TOKEN': TOKEN},
                       {'COOLIFY_WEBHOOK': WEBHOOK, 'COOLIFY_TOKEN': TOKEN + '\n'}):
            with self.subTest(values=list(values)), self.assertRaises(ValueError):
                DeploymentTarget.from_environment(values)
        for webhook in (
            WEBHOOK.replace('https:', 'http:'), WEBHOOK + '#fragment',
            WEBHOOK.replace('coolify.example.test', 'user:secret@coolify.example.test'),
            WEBHOOK.replace('coolify.example.test', 'coolify.example.test:bad'),
            WEBHOOK.replace('/deploy?', '/other?'), WEBHOOK.replace('test-website', 'a,b'),
            WEBHOOK + '&uuid=second', WEBHOOK + '&tag=all', WEBHOOK + '&pr=1',
            WEBHOOK.replace('force=false', 'force=yes'), WEBHOOK + '&cancel_previous=invalid',
        ):
            with self.subTest(webhook=webhook), self.assertRaises(ValueError):
                self.target(webhook)

    def test_token_and_webhook_are_redacted_from_diagnostics(self):
        target = self.target()
        self.assertNotIn(TOKEN, repr(target))
        self.assertNotIn(WEBHOOK, repr(target))
        opener = Mock()
        opener.open.side_effect = urllib.error.URLError(WEBHOOK + TOKEN)
        with self.assertRaises(ValueError) as failure:
            request_deployment(target, opener=opener)
        self.assertNotIn(TOKEN, str(failure.exception))
        self.assertNotIn(WEBHOOK, str(failure.exception))
        self.assertEqual(opener.open.call_count, 1)

    def test_matching_queued_deployment_uses_bearer_post_and_bounded_read(self):
        opener = self.response()
        self.assertEqual(request_deployment(self.target(), opener=opener), 'queued-test-deployment')
        request = opener.open.call_args.args[0]
        self.assertEqual(request.full_url, WEBHOOK)
        self.assertEqual(request.method, 'POST')
        self.assertEqual(request.data, b'')
        self.assertEqual(request.get_header('Authorization'), 'Bearer ' + TOKEN)
        self.assertEqual(request.get_header('User-agent'), 'KNet-Website-Deployment/1.0')
        self.assertEqual(opener.open.call_args.kwargs, {'timeout': 30})
        opener.open.return_value.__enter__.return_value.read.assert_called_once_with(MAX_RESPONSE_BYTES + 1)

    def test_post_preserves_validated_resource_force_and_cancellation_query_options(self):
        for force in ('false', 'true'):
            for cancellation in ('none', 'queued', 'building'):
                with self.subTest(force=force, cancellation=cancellation):
                    webhook = WEBHOOK.replace('force=false', 'force=' + force) + '&cancel_previous=' + cancellation
                    opener = self.response()
                    self.assertEqual(request_deployment(self.target(webhook), opener=opener), 'queued-test-deployment')
                    request = opener.open.call_args.args[0]
                    self.assertEqual(request.full_url, webhook)
                    self.assertEqual(request.method, 'POST')
                    self.assertEqual(request.data, b'')
                    self.assertEqual(opener.open.call_count, 1)

    def test_real_http_post_only_endpoint_receives_one_authorized_resource_command(self):
        requests = []

        class PostOnlyEndpoint(BaseHTTPRequestHandler):
            def reply(self, status, payload):
                body = json.dumps(payload).encode()
                self.send_response(status)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_GET(self):
                requests.append({'method': 'GET'})
                self.reply(405, {'message': 'This endpoint has changed to a POST request.'})

            def do_POST(self):
                url = urllib.parse.urlsplit(self.path)
                query = urllib.parse.parse_qs(url.query)
                requests.append({'method': 'POST', 'path': url.path, 'query': query})
                if self.headers.get('Authorization') != 'Bearer ' + TOKEN:
                    return self.reply(401, {'message': 'Unauthenticated.'})
                if (url.path != '/api/v1/deploy' or query != {
                    'uuid': ['test-website'], 'force': ['true'], 'cancel_previous': ['queued'],
                } or self.headers.get('Content-Length') != '0'
                        or self.headers.get('User-Agent') != 'KNet-Website-Deployment/1.0'):
                    return self.reply(400, {'message': 'Invalid request.'})
                self.reply(200, {'deployments': [
                    {'resource_uuid': 'test-website', 'deployment_uuid': 'queued-test-deployment'},
                ]})

            def log_message(self, format, *args):
                pass

        # The configuration tests require HTTPS. This isolated loopback adapter uses only a synthetic token.
        with HTTPServer(('127.0.0.1', 0), PostOnlyEndpoint) as server:
            worker = threading.Thread(target=server.serve_forever, kwargs={'poll_interval': 0.01}, daemon=True)
            worker.start()
            try:
                webhook = ('http://127.0.0.1:' + str(server.server_port)
                           + '/api/v1/deploy?uuid=test-website&force=true&cancel_previous=queued')
                target = DeploymentTarget(webhook, TOKEN, 'test-website')
                self.assertEqual(request_deployment(target), 'queued-test-deployment')
            finally:
                server.shutdown()
                worker.join(timeout=2)
        self.assertEqual(requests, [{'method': 'POST', 'path': '/api/v1/deploy', 'query': {
            'uuid': ['test-website'], 'force': ['true'], 'cancel_previous': ['queued'],
        }}])

    def test_nonqueued_wrong_resource_or_malformed_success_does_not_claim_success(self):
        for body in (b'not JSON', b'[]', b'{}', b'{"deployments":[]}', b'{"deployments":[null]}',
                     b'x' * (MAX_RESPONSE_BYTES + 1),
                     b'{"deployments":[{"resource_uuid":"other","deployment_uuid":"valid-id"}]}',
                     b'{"deployments":[{"resource_uuid":"test-website"}]}',
                     b'{"deployments":[{"resource_uuid":"test-website","deployment_uuid":"unsafe\\ntext"}]}',
                     b'{"deployments":[{},{}]}', b'[' * 1500, b'\xff'):
            with self.subTest(body=body[:80]), self.assertRaises(ValueError):
                request_deployment(self.target(), opener=self.response(body=body))
        with self.assertRaises(ValueError):
            request_deployment(self.target(), opener=self.response(status=202))

    def test_http_rejection_is_sanitized_and_never_retried(self):
        for status in (302, 401, 403, 405, 429, 500):
            opener = Mock()
            opener.open.side_effect = urllib.error.HTTPError(WEBHOOK, status, TOKEN, {}, io.BytesIO(TOKEN.encode()))
            with self.subTest(status=status), self.assertRaises(ValueError) as failure:
                request_deployment(self.target(), opener=opener)
            self.assertIn(str(status), str(failure.exception))
            self.assertNotIn(TOKEN, str(failure.exception))
            self.assertEqual(opener.open.call_count, 1)

    def assert_rejection(self, body, expected, headers=None, status=403):
        opener = Mock()
        stream = io.BytesIO(body)
        error = urllib.error.HTTPError(WEBHOOK, status, TOKEN, headers or {}, stream)
        error.read = Mock(wraps=error.read)
        opener.open.side_effect = error
        with self.assertRaises(ValueError) as failure:
            request_deployment(self.target(), opener=opener)
        self.assertIn(expected.value, str(failure.exception))
        self.assertIn(str(status), str(failure.exception))
        self.assertNotIn(TOKEN, str(failure.exception))
        self.assertNotIn(WEBHOOK, str(failure.exception))
        self.assertTrue(stream.closed)
        error.read.assert_called_once_with(MAX_RESPONSE_BYTES + 1)
        self.assertEqual(opener.open.call_count, 1)

    def test_coolify_api_disabled_allowlist_and_deploy_permission_are_distinguished(self):
        for message, reason in (
            ('API is disabled.', RejectionReason.API_DISABLED),
            ('You are not allowed to access the API.', RejectionReason.IP_REJECTED),
            ('Missing required permissions: deploy', RejectionReason.DEPLOY_PERMISSION),
        ):
            body = json.dumps({'success': True, 'message': message, 'private': TOKEN + WEBHOOK}).encode()
            with self.subTest(reason=reason):
                self.assert_rejection(body, reason)

    def test_cloudflare_challenge_is_distinguished_without_logging_headers_or_html(self):
        headers = Message()
        headers['CF-Mitigated'] = 'challenge'
        headers['Content-Type'] = 'text/html'
        headers['Set-Cookie'] = TOKEN
        self.assert_rejection(('<html>' + TOKEN + WEBHOOK).encode(), RejectionReason.CLOUDFLARE_CHALLENGE,
                              headers=headers)

    def test_method_rejection_reports_compatibility_failure_without_retry_or_method_fallback(self):
        headers = Message()
        headers['Allow'] = 'POST'
        self.assert_rejection(json.dumps({'message': TOKEN + WEBHOOK}).encode(), RejectionReason.METHOD_NOT_ALLOWED,
                              headers=headers, status=405)

    def test_cloudflare_transit_does_not_misclassify_a_coolify_rejection(self):
        headers = Message()
        headers['Server'] = 'cloudflare'
        headers['CF-Ray'] = 'synthetic-ray'
        headers['Content-Type'] = 'application/json'
        self.assert_rejection(b'{"message":"API is disabled."}', RejectionReason.API_DISABLED, headers=headers)

    def test_cloudflare_signature_rejection_is_recognized_without_echoing_provider_details(self):
        body = json.dumps({'cloudflare_error': True, 'error_code': 1010, 'detail': TOKEN + WEBHOOK}).encode()
        self.assert_rejection(body, RejectionReason.CLOUDFLARE_SIGNATURE)
        for code, marker in ((1010, False), ('1010', True), (1000, True)):
            with self.subTest(code=code, marker=marker):
                body = json.dumps({'cloudflare_error': marker, 'error_code': code}).encode()
                self.assert_rejection(body, RejectionReason.JSON_RESPONSE)

    def test_html_403_points_to_proxy_checks_without_claiming_cloudflare_is_the_source(self):
        headers = Message()
        headers['content-type'] = 'Text/HTML; charset=utf-8'
        self.assert_rejection(('<html>' + TOKEN + WEBHOOK).encode(), RejectionReason.HTML_RESPONSE, headers=headers)

    def test_unknown_json_never_echoes_provider_message_and_does_not_claim_a_specific_cause(self):
        for message in (TOKEN + WEBHOOK, ['API is disabled.'], None):
            with self.subTest(message_type=type(message).__name__):
                self.assert_rejection(json.dumps({'message': message}).encode(), RejectionReason.JSON_RESPONSE)

    def test_invalid_or_oversized_error_bodies_keep_a_safe_fallback(self):
        for body in (b'not JSON', b'\xff', b'[]', b'x' * (MAX_RESPONSE_BYTES + 1), b'[' * 1500):
            with self.subTest(body_size=len(body)):
                self.assert_rejection(body, RejectionReason.UNKNOWN)

    def test_error_body_read_failure_preserves_http_status_and_closes_response(self):
        opener = Mock()
        error = urllib.error.HTTPError(WEBHOOK, 403, TOKEN, {}, io.BytesIO(b''))
        error.read = Mock(side_effect=OSError(TOKEN))
        error.close = Mock(wraps=error.close)
        opener.open.side_effect = error
        with self.assertRaises(ValueError) as failure:
            request_deployment(self.target(), opener=opener)
        self.assertIn('HTTP 403', str(failure.exception))
        self.assertIn(RejectionReason.UNKNOWN.value, str(failure.exception))
        self.assertNotIn(TOKEN, str(failure.exception))
        error.close.assert_called_once_with()
        self.assertEqual(opener.open.call_count, 1)

    def test_redirect_never_forwards_authorization(self):
        request = urllib.request.Request(WEBHOOK, headers={'Authorization': 'Bearer ' + TOKEN})
        self.assertIsNone(RejectRedirects().redirect_request(
            request, None, 302, 'redirect', {}, 'https://other.example.test/collect',
        ))

    def test_network_and_broken_response_errors_are_sanitized_and_not_retried(self):
        for error in (TimeoutError(TOKEN), OSError(TOKEN), http.client.BadStatusLine(TOKEN)):
            opener = Mock()
            opener.open.side_effect = error
            with self.subTest(error=type(error).__name__), self.assertRaises(ValueError) as failure:
                request_deployment(self.target(), opener=opener)
            self.assertNotIn(TOKEN, str(failure.exception))
            self.assertEqual(opener.open.call_count, 1)

    def test_check_mode_never_sends_and_failed_configuration_returns_nonzero(self):
        output = io.StringIO()
        environment = {'COOLIFY_WEBHOOK': WEBHOOK, 'COOLIFY_TOKEN': TOKEN}
        with patch.dict('os.environ', environment, clear=True), patch('sys.argv', ['coolify.py', '--check']), \
                patch('coolify.request_deployment') as sender, contextlib.redirect_stdout(output):
            self.assertEqual(main(), 0)
            sender.assert_not_called()
        self.assertNotIn(TOKEN, output.getvalue())
        self.assertNotIn(WEBHOOK, output.getvalue())
        with patch.dict('os.environ', {}, clear=True), patch('sys.argv', ['coolify.py']), \
                patch('coolify.request_deployment') as sender, contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(main(), 1)
            sender.assert_not_called()


if __name__ == '__main__':
    unittest.main()
