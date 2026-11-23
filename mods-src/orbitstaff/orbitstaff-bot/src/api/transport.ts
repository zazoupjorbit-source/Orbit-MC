/** Requete HTTP sortante (donnee pure, testable sans reseau). */
export interface HttpRequest {
  method: string;
  url: string;
  headers: Record<string, string>;
  body?: string;
}

/** Reponse HTTP. */
export interface HttpResponse {
  status: number;
  body: string;
}

/** Port de transport HTTP : abstrait pour tester le client API sans reseau reel. */
export interface HttpTransport {
  send(request: HttpRequest): Promise<HttpResponse>;
}

/** Transport reel base sur fetch (disponible nativement sous Node 20+). */
export class FetchTransport implements HttpTransport {
  async send(request: HttpRequest): Promise<HttpResponse> {
    const response = await fetch(request.url, {
      method: request.method,
      headers: request.headers,
      body: request.body,
    });
    const body = await response.text();
    return { status: response.status, body };
  }
}
