#!/usr/bin/env bash

set -xeo pipefail

readonly live_run=${LIVE_RUN:-false}
# Release number
readonly version=${VERSION:?input VERSION is required}
# Git actor name
readonly git_user_name=${GIT_USER_NAME:?input GIT_USER_NAME is required}
# Git actor email
readonly git_user_email=${GIT_USER_EMAIL:?input GIT_USER_EMAIL is required}
# The zenoh-flat-jni release to build against, if it is moving with this release
readonly flat_jni_version=${FLAT_JNI_VERSION:-''}

export GIT_AUTHOR_NAME=$git_user_name
export GIT_AUTHOR_EMAIL=$git_user_email
export GIT_COMMITTER_NAME=$git_user_name
export GIT_COMMITTER_EMAIL=$git_user_email

# Bump Gradle project version. There is no Cargo manifest here any more: the
# native libraries live inside the zenoh-flat-jni artifact this SDK depends on.
printf '%s' "$version" > version.txt

git commit version.txt -m "chore: Bump version to \`$version\`"

# Point at the zenoh-flat-jni release this SDK is built against.
if [[ -n "$flat_jni_version" ]]; then
  sed -i.bak -E "s|^zenohFlatJniVersion=.*|zenohFlatJniVersion=$flat_jni_version|" gradle.properties
  rm -f gradle.properties.bak

  # Only commit when it actually moved: `git commit` on an unchanged file exits
  # non-zero, which under `set -e` would abort the release before tagging.
  if git diff --quiet gradle.properties; then
    echo "note: already building against zenoh-flat-jni $flat_jni_version"
  else
    git diff gradle.properties
    git commit gradle.properties -m "chore: Build against zenoh-flat-jni \`$flat_jni_version\`"
  fi
fi

# Checked on the value the release will actually build against, not on the
# workflow input: main now inherits `1.9.0-kotlin-SNAPSHOT`, our own mutable copy
# of zenoh-flat-jni, so omitting the input is exactly how a release would reach
# a snapshot dependency — which the earlier input-only check did not cover.
# A rehearsal may depend on one; that is how the SDK is exercised before the
# binding is released at all.
effective_flat_jni_version=$(sed -n 's/^zenohFlatJniVersion=//p' gradle.properties | tr -d '[:space:]')
case "$effective_flat_jni_version" in
  *-SNAPSHOT)
    if [[ "$live_run" == "true" ]]; then
      echo "error: refusing to release against a snapshot dependency ($effective_flat_jni_version)." >&2
      echo "       Pass zenoh-flat-jni-version naming a release that is on Maven Central." >&2
      exit 1
    fi
    echo "note: rehearsing against snapshot $effective_flat_jni_version"
    ;;
esac

if [[ ${live_run} ]]; then
  git tag --force "$version" -m "v$version"
fi
git log -10
git show-ref --tags
git push origin
git push --force origin "$version"
