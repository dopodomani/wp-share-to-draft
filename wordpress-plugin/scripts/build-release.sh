#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
plugin_source="$(cd "${script_dir}/.." && pwd)"
output_dir="${1:-${plugin_source}/dist}"
archive_name="${2:-material-capture.zip}"

mkdir -p "${output_dir}"
output_dir="$(cd "${output_dir}" && pwd)"
archive_path="${output_dir}/${archive_name}"
work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

package_root="${work_dir}/material-capture"
mkdir -p "${package_root}"

cp "${plugin_source}/material-capture.php" "${package_root}/"
cp "${plugin_source}/uninstall.php" "${package_root}/"
cp "${plugin_source}/composer.json" "${package_root}/"
cp "${plugin_source}/composer.lock" "${package_root}/"
cp -R "${plugin_source}/includes" "${package_root}/"

COMPOSER_ALLOW_SUPERUSER=1 composer install \
    --working-dir="${package_root}" \
    --no-dev \
    --classmap-authoritative \
    --no-interaction \
    --no-progress \
    --prefer-dist

# Composer metadata is not needed at runtime; vendor/autoload.php and vendor/composer/** are.
rm "${package_root}/composer.json" "${package_root}/composer.lock"
rm -f "${archive_path}"
(cd "${work_dir}" && zip -X -q -r "${archive_path}" material-capture)

printf '%s\n' "${archive_path}"
