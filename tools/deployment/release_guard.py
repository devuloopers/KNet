#!/usr/bin/env python3
"""Fail closed unless an eligible website release still matches the current master commit."""
import os
import re
import subprocess
import sys

RELEASE_BRANCH = 'master'
SHA = re.compile(r'[a-f0-9]{40}\Z')
REPOSITORY = re.compile(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z')


def read_head(repository, branch):
    """Read one authenticated GitHub branch ref; never print gh diagnostics or its credentials."""
    try:
        result = subprocess.run(
            ['gh', 'api', f'repos/{repository}/git/ref/heads/{branch}', '--jq', '.object.sha'],
            check=True, capture_output=True, text=True, timeout=20,
        )
    except (OSError, subprocess.SubprocessError):
        raise ValueError('Could not verify the current release commit; publication/deployment is stopped.') from None
    head = result.stdout.strip()
    if not SHA.fullmatch(head):
        raise ValueError('GitHub did not return a valid release commit; publication/deployment is stopped.')
    return head


def is_current_release(environment, head_reader=read_head):
    """Permit push/manual master sources only, and compare the checked commit with the live ref."""
    if (environment.get('GITHUB_EVENT_NAME') not in ('push', 'workflow_dispatch')
            or environment.get('GITHUB_REF') != f'refs/heads/{RELEASE_BRANCH}'):
        return False
    repository = environment.get('GITHUB_REPOSITORY', '')
    source = environment.get('GITHUB_SHA', '')
    if not REPOSITORY.fullmatch(repository) or not SHA.fullmatch(source):
        raise ValueError('The website release context is invalid; publication/deployment is stopped.')
    head = head_reader(repository, RELEASE_BRANCH)
    if not SHA.fullmatch(head):
        raise ValueError('The current release commit is invalid; publication/deployment is stopped.')
    return source == head


def main():
    """Emit one GitHub step-output line; unexpected or unverifiable state exits unsuccessfully."""
    try:
        current = is_current_release(os.environ)
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 1
    print('current=' + str(current).lower())
    if not current:
        print('This is not the current eligible master release; promotion/deployment is skipped.', file=sys.stderr)
    return 0


if __name__ == '__main__':
    sys.exit(main())
