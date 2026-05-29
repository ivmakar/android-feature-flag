# Security Policy

## Supported versions

This library is pre-1.0. Security fixes are applied to the latest released version on `main`. Older tags are not patched — upgrade to the latest release.

## Reporting a vulnerability

**Do not open a public issue for security vulnerabilities.**

Report privately via GitHub's [private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability) on this repository (Security tab → "Report a vulnerability").

Please include:

- A description of the issue and its impact.
- Steps to reproduce or a proof of concept.
- The affected version(s).

## Response

- Acknowledgement within **5 business days**.
- An assessment and remediation plan once the report is triaged.
- Coordinated disclosure: a fix is released before public details are published, and reporters are credited unless they prefer to remain anonymous.

## Scope

This is a client-side Android library with no network calls and no built-in persistence of secrets. Relevant concerns include, but are not limited to:

- Debug-only sources (`BuildVariantSource`, `PersistentOverrideSource`) leaking into a release build and altering flag resolution.
- A flag value crashing the resolver instead of falling through to a safe default.

Misconfiguration in a consuming app (e.g. storing secrets in flag values) is out of scope.
