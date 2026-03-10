# Security Notes

## Tailscale Serve HTTPS + Optional Auth Header

For production-ish usage, expose the webhook over HTTPS via Tailscale Serve instead of raw LAN HTTP.

### 1) Serve the webhook through Tailscale HTTPS

Example (service listening locally on port 3000):

```bash
tailscale serve https / http://127.0.0.1:3000
```

This gives you a `https://<device>.<tailnet>.ts.net` endpoint with TLS handled by Tailscale.

### 2) Restrict exposure

- Keep the node private to your tailnet.
- Avoid opening public ingress/port-forwarding.
- Prefer ACL-restricted users/devices in Tailscale admin.

### 3) Optional shared-token header auth

You can require a simple token header to reduce accidental/unauthorized uploads.

Set env var on server:

```bash
export UPLOAD_AUTH_TOKEN="replace-with-long-random-token"
```

Then clients must include:

```http
x-upload-token: <UPLOAD_AUTH_TOKEN>
```

If the token is set and header is missing/wrong, server should return `401`.

### 4) Android config recommendation

Use a full HTTPS base URL and webhook path:

- Host: `https://<device>.<tailnet>.ts.net`
- Upload path: `/webhook/upload-voice-memo`

This avoids cleartext traffic and keeps transfer encrypted end-to-end.
