"""Exercise the exact current-commit gate used before candidate publication, promotion and deploy."""
import contextlib
import io
import subprocess
import unittest
from unittest.mock import Mock, patch

from release_guard import is_current_release, main, read_head

SOURCE = 'a' * 40
NEWER = 'b' * 40
ENVIRONMENT = {
    'GITHUB_EVENT_NAME': 'push', 'GITHUB_REF': 'refs/heads/master',
    'GITHUB_REPOSITORY': 'devuloopers/KNet', 'GITHUB_SHA': SOURCE,
}


class WebsiteReleaseGuardTest(unittest.TestCase):
    """Checks use synthetic GitHub context and an injected ref reader, never the live repository."""

    def test_current_push_and_manual_master_sources_are_eligible(self):
        for event in ('push', 'workflow_dispatch'):
            reader = Mock(return_value=SOURCE)
            self.assertTrue(is_current_release({**ENVIRONMENT, 'GITHUB_EVENT_NAME': event}, reader))
            reader.assert_called_once_with('devuloopers/KNet', 'master')

    def test_pull_requests_and_other_events_never_read_or_promote_a_branch(self):
        for event in ('pull_request', 'pull_request_target', 'workflow_run', 'schedule', ''):
            reader = Mock()
            self.assertFalse(is_current_release({**ENVIRONMENT, 'GITHUB_EVENT_NAME': event}, reader))
            reader.assert_not_called()

    def test_other_branches_tags_and_missing_refs_never_deploy(self):
        for reference in ('refs/heads/main', 'refs/heads/feature', 'refs/tags/master', 'master', ''):
            reader = Mock()
            self.assertFalse(is_current_release({**ENVIRONMENT, 'GITHUB_REF': reference}, reader))
            reader.assert_not_called()

    def test_superseded_source_is_not_current(self):
        self.assertFalse(is_current_release(ENVIRONMENT, Mock(return_value=NEWER)))

    def test_changed_head_between_pipeline_stages_revokes_promotion_and_deployment(self):
        reader = Mock(side_effect=[SOURCE, NEWER, NEWER])
        self.assertTrue(is_current_release(ENVIRONMENT, reader))
        self.assertFalse(is_current_release(ENVIRONMENT, reader))
        self.assertFalse(is_current_release(ENVIRONMENT, reader))

    def test_malformed_context_fails_before_running_gh(self):
        for key, value in (('GITHUB_REPOSITORY', ''), ('GITHUB_REPOSITORY', 'owner/repo/extra'),
                           ('GITHUB_REPOSITORY', 'owner/\nrepo'), ('GITHUB_SHA', ''),
                           ('GITHUB_SHA', SOURCE + '\n'), ('GITHUB_SHA', 'not-a-commit')):
            reader = Mock()
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                is_current_release({**ENVIRONMENT, key: value}, reader)
            reader.assert_not_called()

    def test_invalid_head_response_cannot_turn_into_a_success(self):
        for head in ('', 'null', SOURCE + '\n', 'not-a-commit'):
            with self.subTest(head=head), self.assertRaises(ValueError):
                is_current_release(ENVIRONMENT, Mock(return_value=head))

    def test_gh_request_uses_exact_repository_ref_and_bounded_execution(self):
        with patch('release_guard.subprocess.run', return_value=Mock(stdout=SOURCE + '\n')) as execute:
            self.assertEqual(read_head('devuloopers/KNet', 'master'), SOURCE)
            execute.assert_called_once_with(
                ['gh', 'api', 'repos/devuloopers/KNet/git/ref/heads/master', '--jq', '.object.sha'],
                check=True, capture_output=True, text=True, timeout=20,
            )

    def test_authentication_network_timeout_and_malformed_data_fail_closed(self):
        secret = 'synthetic-private-diagnostic'
        for error in (OSError(secret), subprocess.CalledProcessError(1, 'gh', stderr=secret),
                      subprocess.TimeoutExpired('gh', 20, stderr=secret)):
            with patch('release_guard.subprocess.run', side_effect=error), self.assertRaises(ValueError) as failure:
                read_head('devuloopers/KNet', 'master')
            self.assertNotIn(secret, str(failure.exception))
        with patch('release_guard.subprocess.run', return_value=Mock(stdout='null')), self.assertRaises(ValueError):
            read_head('devuloopers/KNet', 'master')

    def test_cli_emits_only_expected_step_outputs(self):
        for current in (True, False):
            output = io.StringIO()
            with patch('release_guard.is_current_release', return_value=current), \
                    contextlib.redirect_stdout(output), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(main(), 0)
            self.assertEqual(output.getvalue(), 'current=' + str(current).lower() + '\n')

    def test_cli_failure_cannot_emit_a_current_output(self):
        output = io.StringIO()
        with patch('release_guard.is_current_release', side_effect=ValueError('Verification failed.')), \
                contextlib.redirect_stdout(output), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(main(), 1)
        self.assertEqual(output.getvalue(), '')


if __name__ == '__main__':
    unittest.main()
