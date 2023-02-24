# Auth0 Guardian Demo
Sample android app using Auth Guardian SDK to register a/ enrol for push notifications and respond to MFA push.

## FCM Setup
- Register for a project in (Firebase)[https://console.firebase.google.com/project]
- Add App of type Android
- Register `auth0.guardian.demo` as teh package name
- Download the `google-services.json` file
- Add it under the `app` directory

## Configure Auth0 To Send Pushnotification

- https://auth0.com/docs/secure/multi-factor-authentication/multi-factor-authentication-factors/configure-push-notifications-for-mfa#configure-push-notifications-for-apple-using-apn-