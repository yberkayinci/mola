#!/usr/bin/env bash
set -euo pipefail

version="9.5.0"
distribution_sha256="553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746"
wrapper_jar_sha256="497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
archive="gradle-${version}-bin.zip"
url="https://services.gradle.org/distributions/${archive}"
script_dir="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(CDPATH= cd -- "${script_dir}/.." && pwd)"
cache_dir="${TMPDIR:-/tmp}/mola-gradle-bootstrap"
archive_path="${cache_dir}/${archive}"
bootstrap_project="${cache_dir}/wrapper-project"

die() {
  echo "$1" >&2
  exit 1
}

hash_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    die "A SHA-256 tool is required to verify Gradle files."
  fi
}

mkdir -p "$cache_dir"

if [ ! -f "$archive_path" ]; then
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 3 --output "$archive_path" "$url"
  elif command -v wget >/dev/null 2>&1; then
    wget --output-document="$archive_path" "$url"
  else
    die "Install curl or wget, then run this script again."
  fi
fi

actual_distribution_sha256="$(hash_file "$archive_path")"
if [ "$actual_distribution_sha256" != "$distribution_sha256" ]; then
  rm -f "$archive_path"
  die "Gradle distribution checksum mismatch. The downloaded archive was removed."
fi

if [ ! -x "${cache_dir}/gradle-${version}/bin/gradle" ]; then
  command -v unzip >/dev/null 2>&1 || die "Install unzip, then run this script again."
  unzip -q -o "$archive_path" -d "$cache_dir"
fi

rm -rf "$bootstrap_project"
mkdir -p "$bootstrap_project"
printf 'rootProject.name = "wrapper-bootstrap"\n' > "${bootstrap_project}/settings.gradle"

(
  cd "$bootstrap_project"
  "${cache_dir}/gradle-${version}/bin/gradle" \
    --no-daemon \
    wrapper \
    --gradle-version "$version" \
    --distribution-type bin
)

mkdir -p "${repo_root}/gradle/wrapper"
cp "${bootstrap_project}/gradlew" "${repo_root}/gradlew"
cp "${bootstrap_project}/gradlew.bat" "${repo_root}/gradlew.bat"
cp "${bootstrap_project}/gradle/wrapper/gradle-wrapper.jar" "${repo_root}/gradle/wrapper/gradle-wrapper.jar"
cp "${bootstrap_project}/gradle/wrapper/gradle-wrapper.properties" "${repo_root}/gradle/wrapper/gradle-wrapper.properties"
chmod +x "${repo_root}/gradlew"

actual_wrapper_sha256="$(hash_file "${repo_root}/gradle/wrapper/gradle-wrapper.jar")"
if [ "$actual_wrapper_sha256" != "$wrapper_jar_sha256" ]; then
  rm -f \
    "${repo_root}/gradlew" \
    "${repo_root}/gradlew.bat" \
    "${repo_root}/gradle/wrapper/gradle-wrapper.jar" \
    "${repo_root}/gradle/wrapper/gradle-wrapper.properties"
  die "Gradle Wrapper JAR checksum mismatch. Generated wrapper files were removed."
fi

properties="${repo_root}/gradle/wrapper/gradle-wrapper.properties"
if grep -q '^distributionSha256Sum=' "$properties"; then
  sed -i.bak "s/^distributionSha256Sum=.*/distributionSha256Sum=${distribution_sha256}/" "$properties"
  rm -f "${properties}.bak"
else
  printf '\ndistributionSha256Sum=%s\n' "$distribution_sha256" >> "$properties"
fi

printf 'Standard Gradle %s wrapper generated; distribution and wrapper JAR checksums verified.\n' "$version"
