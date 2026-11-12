#!/usr/bin/env bash
# Decides whether the checked-out commit is a release by comparing the pom.xml version with the newest v* tag and
# the newest versioned CHANGELOG.md entry.
#
#   Skip    (exit 0, release=false): SNAPSHOT or non X.Y.Z version, or the version is already tagged
#   Fail    (exit 1):                version lower than the latest tag, or no matching CHANGELOG entry
#   Release (exit 0, release=true):  version higher than the latest tag (or no tag yet) with a matching entry
#
# Outputs release, version and notes_file to $GITHUB_OUTPUT (stdout when run locally). The environment variables
# POM_VERSION, CHANGELOG_FILE and NOTES_FILE override the defaults, which is how the script is tested locally.
set -euo pipefail

changelog="${CHANGELOG_FILE:-CHANGELOG.md}"
notes_file="${NOTES_FILE:-${RUNNER_TEMP:-/tmp}/release-notes.md}"
output="${GITHUB_OUTPUT:-/dev/stdout}"

skip() {
    echo "Skip: $1"
    echo "release=false" >> "$output"
    exit 0
}

fail() {
    echo "::error::$1"
    exit 1
}

version="${POM_VERSION:-$(mvn -B -q help:evaluate -Dexpression=project.version -DforceStdout)}"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    skip "pom.xml version '$version' is not a plain X.Y.Z release version."
fi

latest_tag="$(git tag -l 'v*' --sort=-v:refname | head -n 1)"
latest="${latest_tag#v}"
if git rev-parse -q --verify "refs/tags/v$version" > /dev/null; then
    skip "version $version is already released as v$version."
fi
if [[ -n "$latest" ]]; then
    highest="$(printf '%s\n%s\n' "$version" "$latest" | sort -V | tail -n 1)"
    if [[ "$highest" != "$version" ]]; then
        fail "pom.xml version $version is lower than the latest release $latest_tag; the version went backwards."
    fi
fi

if [[ ! -f "$changelog" ]]; then
    fail "$changelog is missing; add a '## [$version] - YYYY-MM-DD' entry."
fi
# The newest versioned heading; '## [Unreleased]' is ignored
top="$(grep -m 1 -E '^## \[[0-9]' "$changelog" | sed -E 's/^## \[([^]]+)\].*/\1/' || true)"
if [[ "$top" != "$version" ]]; then
    fail "pom.xml is at $version but the newest $changelog entry is '${top:-none}'; add a '## [$version] - YYYY-MM-DD' entry above the others."
fi

awk -v heading="## [$version]" '
    index($0, heading) == 1 { found = 1; next }
    found && /^## \[/ { exit }
    found { print }
' "$changelog" > "$notes_file"
if ! grep -q '[^[:space:]]' "$notes_file"; then
    fail "The $changelog entry for $version is empty."
fi

echo "Release: $version (previous: ${latest_tag:-none})."
{
    echo "release=true"
    echo "version=$version"
    echo "notes_file=$notes_file"
} >> "$output"
