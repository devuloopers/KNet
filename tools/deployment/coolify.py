#!/usr/bin/env python3
"""Request one authenticated Coolify deployment without logging credentials or response bodies."""
import argparse
from dataclasses import dataclass
from enum import Enum
import http.client
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

IDENTIFIER = re.compile(r'[A-Za-z0-9_-]{1,128}\Z')
MAX_RESPONSE_BYTES = 64 * 1024


class RejectionReason(Enum):
    """Fixed public diagnostics; provider messages and credentials never become output."""
    AUTHENTICATION = 'Check the complete Coolify token, its expiry and team membership.'
    METHOD_NOT_ALLOWED = 'The endpoint rejected the method; confirm it supports POST /api/v1/deploy.'
    API_DISABLED = 'Coolify reports that API access is disabled; enable it in Settings > Advanced.'
    IP_REJECTED = 'Coolify reports that the caller is outside its API IP allowlist.'
    DEPLOY_PERMISSION = 'Coolify reports a missing deploy permission; check the token permissions.'
    CLOUDFLARE_CHALLENGE = 'Cloudflare challenged this request before it reached the deployment API.'
    CLOUDFLARE_SIGNATURE = 'Cloudflare error 1010 blocked the client signature; check Browser Integrity Check rules.'
    HTML_RESPONSE = 'An HTML rejection was returned; check Cloudflare Access/WAF and reverse-proxy rules.'
    JSON_RESPONSE = 'An unrecognized JSON rejection was returned; check token permissions and team/resource access.'
    UNKNOWN = 'Inspect Coolify and proxy logs for the rejection reason.'


def rejection_reason(status, headers, body):
    """Classify a bounded rejection using only explicitly recognized provider evidence."""
    if headers.get('cf-mitigated') == 'challenge':
        return RejectionReason.CLOUDFLARE_CHALLENGE
    if status == 401:
        return RejectionReason.AUTHENTICATION
    if status == 405:
        return RejectionReason.METHOD_NOT_ALLOWED
    if status != 403:
        return RejectionReason.UNKNOWN
    if len(body) <= MAX_RESPONSE_BYTES:
        try:
            payload = json.loads(body)
        except (ValueError, UnicodeError, RecursionError):
            payload = None
        message = payload.get('message') if isinstance(payload, dict) else None
        if isinstance(payload, dict) and payload.get('cloudflare_error') is True and payload.get('error_code') == 1010:
            return RejectionReason.CLOUDFLARE_SIGNATURE
        if message == 'API is disabled.':
            return RejectionReason.API_DISABLED
        if message == 'You are not allowed to access the API.':
            return RejectionReason.IP_REJECTED
        if message == 'Missing required permissions: deploy':
            return RejectionReason.DEPLOY_PERMISSION
        if isinstance(payload, dict):
            return RejectionReason.JSON_RESPONSE
    if headers.get('Content-Type', '').split(';', 1)[0].strip().lower() == 'text/html':
        return RejectionReason.HTML_RESPONSE
    return RejectionReason.UNKNOWN


@dataclass(frozen=True, repr=False)
class DeploymentTarget:
    """Single-resource HTTPS webhook and bearer credential, always redacted in diagnostics."""
    webhook: str
    token: str
    resource: str

    def __repr__(self):
        return 'DeploymentTarget(<redacted>)'

    @classmethod
    def from_environment(cls, environment):
        """Validate secret syntax before any request; reject ambiguous/multi-resource targets."""
        webhook = environment.get('COOLIFY_WEBHOOK', '')
        token = environment.get('COOLIFY_TOKEN', '')
        if not webhook or not token:
            raise ValueError('Configure COOLIFY_WEBHOOK and COOLIFY_TOKEN as GitHub secrets.')
        if any(ord(char) <= 32 or ord(char) >= 127 for char in webhook + token):
            raise ValueError('Coolify secrets must not contain whitespace or control characters.')
        try:
            url = urllib.parse.urlsplit(webhook)
            port = url.port
            query = urllib.parse.parse_qs(url.query, keep_blank_values=True, strict_parsing=True)
        except ValueError:
            raise ValueError('Invalid Coolify deployment webhook.') from None
        if (url.scheme != 'https' or not url.hostname or url.username is not None
                or url.password is not None or url.fragment or url.path != '/api/v1/deploy'
                or port == 0):
            raise ValueError('Use the HTTPS Coolify /api/v1/deploy resource webhook without embedded credentials.')
        if (set(query) - {'uuid', 'force', 'cancel_previous'} or any(len(values) != 1 for values in query.values())
                or not IDENTIFIER.fullmatch(query.get('uuid', [''])[0])):
            raise ValueError('The webhook must target exactly one resource UUID, without tags or previews.')
        if query.get('force', ['false'])[0] not in ('true', 'false'):
            raise ValueError('Invalid Coolify force option.')
        if query.get('cancel_previous', ['none'])[0] not in ('none', 'queued', 'building'):
            raise ValueError('Invalid Coolify cancellation option.')
        return cls(webhook, token, query['uuid'][0])


class RejectRedirects(urllib.request.HTTPRedirectHandler):
    """Never forward a bearer token to a redirect destination."""
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request_deployment(target, opener=None):
    """Send one bounded POST and return its matching queued identifier; never retry implicitly."""
    opener = opener or urllib.request.build_opener(RejectRedirects())
    request = urllib.request.Request(
        target.webhook,
        headers={'Authorization': 'Bearer ' + target.token, 'Accept': 'application/json',
                 'User-Agent': 'KNet-Website-Deployment/1.0'},
        data=b'',
        method='POST',
    )
    try:
        with opener.open(request, timeout=30) as response:
            if response.status != 200:
                raise ValueError('Coolify did not confirm a queued deployment.')
            body = response.read(MAX_RESPONSE_BYTES + 1)
    except urllib.error.HTTPError as error:
        try:
            reason = rejection_reason(error.code, error.headers, error.read(MAX_RESPONSE_BYTES + 1))
        except (OSError, http.client.HTTPException):
            reason = RejectionReason.UNKNOWN
        finally:
            error.close()
        raise ValueError(f'Deployment endpoint rejected the request (HTTP {error.code}). {reason.value}') from None
    except (OSError, http.client.HTTPException):
        raise ValueError('Coolify request failed or timed out; inspect Coolify before retrying.') from None
    if len(body) > MAX_RESPONSE_BYTES:
        raise ValueError('Coolify response exceeded the allowed size.')
    try:
        payload = json.loads(body)
    except (ValueError, RecursionError):
        raise ValueError('Coolify returned an invalid deployment response.') from None
    deployments = payload.get('deployments') if isinstance(payload, dict) else None
    if not isinstance(deployments, list) or len(deployments) != 1 or not isinstance(deployments[0], dict):
        raise ValueError('Coolify did not return exactly one queued deployment.')
    deployment = deployments[0]
    identifier = deployment.get('deployment_uuid')
    if (deployment.get('resource_uuid') != target.resource or not isinstance(identifier, str)
            or not IDENTIFIER.fullmatch(identifier)):
        raise ValueError('Coolify returned a deployment for an unexpected resource or without its identifier.')
    return identifier


def main():
    """Run configuration-only validation or queue one deployment with sanitized diagnostics."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Validate configuration without calling Coolify.')
    args = parser.parse_args()
    try:
        target = DeploymentTarget.from_environment(os.environ)
        if args.check:
            print('Coolify deployment configuration is valid; no request sent.')
        else:
            deployment = request_deployment(target)
            print(f'Coolify queued deployment `{deployment}`. Confirm its health in Coolify Deployment Logs.')
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
