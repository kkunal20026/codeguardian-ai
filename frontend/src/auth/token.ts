type TokenProvider = () => Promise<string | null>;

let provider: TokenProvider = async () => null;

export function setTokenProvider(next: TokenProvider): void {
  provider = next;
}

export function accessToken(): Promise<string | null> {
  return provider();
}
