#!/usr/bin/env bash
set -euo pipefail

# The workflow is serialized, while the push below also protects against tags
# created outside this workflow between the fetch and the reservation.
for attempt in {1..10}; do
  git fetch --tags origin
  existing=$(git tag --points-at "$GITHUB_SHA" --list 'v0.0.*' | grep -E '^v0\.0\.[1-9][0-9]*$' | sort -V | tail -n 1 || true)
  if [[ -n "$existing" ]]; then
    tag=$existing
    break
  fi
  latest=$(git tag --list 'v0.0.*' | grep -E '^v0\.0\.[1-9][0-9]*$' | sort -V | tail -n 1 || true)
  patch=${latest##*.}
  patch=${patch:-0}
  next=$((patch + 1))
  if (( next > 2100000000 )); then
    echo 'Android versionCode limit reached' >&2
    exit 1
  fi
  tag="v0.0.$next"
  git tag "$tag" "$GITHUB_SHA"
  if git push origin "refs/tags/$tag"; then
    break
  fi
  git tag -d "$tag"
  tag=''
done
if [[ -z "$tag" ]]; then
  echo 'Unable to reserve a version tag after 10 attempts' >&2
  exit 1
fi
printf 'tag=%s\nversion=%s\ncode=%s\n' "$tag" "${tag#v}" "${tag##*.}" >> "$GITHUB_OUTPUT"
