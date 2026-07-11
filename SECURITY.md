# Security Policy

## Supported releases

Only release artifacts built after the ERP slimming and security verification are supported.
All ZIP, JAR, image, and database packages produced before 2026-07-11 are revoked and must not be deployed.

## Credential handling

- Credentials must be injected through environment variables or an external secret manager.
- SQL, YAML, source code, logs, images, and release archives must not contain live credentials.
- SMS, DingTalk, WeChat, social-login, object-storage, and AI credentials that ever appeared in the upstream baseline must be rotated by the owning platform administrator.
- A Git history scan and a release-artifact scan are required before every production release.

## Reporting

Report suspected exposure privately to the repository owner. Do not open a public issue containing credentials or customer data.
