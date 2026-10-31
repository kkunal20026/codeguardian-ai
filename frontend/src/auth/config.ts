export const authMode: 'demo' | 'keycloak' =
  (import.meta.env.VITE_AUTH_MODE ?? 'demo').toLowerCase() === 'keycloak' ? 'keycloak' : 'demo';

export const keycloakConfig = {
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:9090',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'code-health',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'code-health-ui'
};
