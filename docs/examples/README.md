# Example Data Files

These files provide the same sample identity/customer/KYC domain in JSON, YAML, and XML:

- `identity-customers.json`
- `identity-customers.yaml`
- `identity-customers.xml`

The examples are shaped for `--cache` query mode. Object names are singular so generated cache tables are easy to query:

- `identity_data`
- `auth`
- `auth_user`
- `login`
- `customer`
- `email_address`
- `address`
- `kyc`
- `status_event`

Most customers have an `auth_user_id` that joins to `auth_user.id`; one customer intentionally has no login. KYC records include a current `status` and a nested `status_event` history for flow-style queries.

`identity-country-counts.nql` queries these files in cache mode and counts customers by residential address country, excluding customers whose KYC status is `not_started`.
