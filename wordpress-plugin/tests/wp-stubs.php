<?php

/**
 * Minimal stand-ins for the WordPress classes/functions this plugin's type declarations
 * reference, so the unit suite can run without a full WordPress install.
 *
 * These are NOT meant to reproduce WordPress's real behavior beyond what's needed for
 * type-checking and simple identity checks (e.g. is_wp_error()). Actual WordPress
 * behavior is verified in the Phase 4 integration suite against a real WordPress
 * instance — see docs/phase2-wordpress-plugin-design.md#integration-test-scope-designed-separately-gates-phase-4.
 */

declare(strict_types=1);

if (!class_exists('WP_Error')) {
    class WP_Error
    {
        /** @var array<string, array<int, string>> */
        private array $errors = [];

        /** @var array<string, mixed> */
        private array $errorData = [];

        public function __construct(string $code = '', string $message = '', mixed $data = '')
        {
            if ($code !== '') {
                $this->errors[$code][] = $message;
                if ($data !== '') {
                    $this->errorData[$code] = $data;
                }
            }
        }

        public function get_error_code(): string
        {
            $codes = array_keys($this->errors);

            return $codes[0] ?? '';
        }

        public function get_error_message(string $code = ''): string
        {
            if ($code === '') {
                $code = $this->get_error_code();
            }

            return $this->errors[$code][0] ?? '';
        }

        public function get_error_data(string $code = ''): mixed
        {
            if ($code === '') {
                $code = $this->get_error_code();
            }

            return $this->errorData[$code] ?? null;
        }
    }
}

if (!function_exists('is_wp_error')) {
    function is_wp_error(mixed $thing): bool
    {
        return $thing instanceof WP_Error;
    }
}

if (!class_exists('WP_REST_Request')) {
    class WP_REST_Request
    {
        /** @param array<string, mixed> $params */
        public function __construct(private array $params = [])
        {
        }

        /** @return array<string, mixed> */
        public function get_params(): array
        {
            return $this->params;
        }

        public function get_param(string $key): mixed
        {
            return $this->params[$key] ?? null;
        }
    }
}

if (!class_exists('WP_REST_Response')) {
    class WP_REST_Response
    {
        public function __construct(private mixed $data = null, private int $status = 200)
        {
        }

        public function get_data(): mixed
        {
            return $this->data;
        }

        public function get_status(): int
        {
            return $this->status;
        }
    }
}

if (!class_exists('WP_REST_Server')) {
    class WP_REST_Server
    {
        public const CREATABLE = 'POST';
    }
}

if (!class_exists('WP_REST_Controller')) {
    class WP_REST_Controller
    {
        protected $namespace = '';
        protected $rest_base = '';
    }
}

if (!class_exists('IXR_Error')) {
    class IXR_Error
    {
        public function __construct(public int $code, public string $message)
        {
        }
    }
}

if (!class_exists('wp_xmlrpc_server')) {
    class wp_xmlrpc_server
    {
        public ?IXR_Error $error = null;

        public function login(string $username, string $password): bool
        {
            return true;
        }
    }
}
