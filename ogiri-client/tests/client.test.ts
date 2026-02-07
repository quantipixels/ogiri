import { describe, it, expect, beforeEach, vi } from 'vitest';
import { OgiriClient } from '../src/client';
import { OgiriAuthError } from '../src/errors';
import type { OgiriTokens } from '../src/types';

const mockTokens: OgiriTokens = {
  accessToken: 'test-token',
  client: 'test-client',
  uid: 'user@example.com',
  expiry: '1234567890',
  tokenType: 'Bearer',
};

describe('OgiriClient', () => {
  let client: OgiriClient;
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    client = new OgiriClient({
      baseURL: 'https://api.example.com',
    });
  });

  describe('token management', () => {
    it('should start with no tokens', () => {
      expect(client.isAuthenticated()).toBe(false);
      expect(client.getTokens()).toBeNull();
    });

    it('should set and get tokens', () => {
      client.setTokens(mockTokens);
      expect(client.isAuthenticated()).toBe(true);
      expect(client.getTokens()).toEqual(mockTokens);
    });

    it('should clear tokens', () => {
      client.setTokens(mockTokens);
      client.clearTokens();
      expect(client.isAuthenticated()).toBe(false);
      expect(client.getTokens()).toBeNull();
    });
  });

  describe('request', () => {
    it('should make unauthenticated request', async () => {
      const mockResponse = { data: 'test' };
      fetchMock.mockResolvedValue(
        new Response(JSON.stringify(mockResponse), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      );

      const result = await client.request('/test');

      expect(fetchMock).toHaveBeenCalledWith(
        'https://api.example.com/test',
        expect.objectContaining({ method: 'GET' })
      );
      expect(result.data).toEqual(mockResponse);
    });

    it('should inject auth headers when authenticated', async () => {
      client.setTokens(mockTokens);
      fetchMock.mockResolvedValue(new Response('{}', { status: 200 }));

      await client.request('/protected');

      const callArgs = fetchMock.mock.calls[0];
      const headers = callArgs[1].headers;

      expect(headers['access-token']).toBe('test-token');
      expect(headers.client).toBe('test-client');
      expect(headers.uid).toBe('user@example.com');
    });

    it('should extract and store rotated tokens from response', async () => {
      client.setTokens(mockTokens);

      const newTokens: OgiriTokens = {
        accessToken: 'rotated-token',
        client: 'rotated-client',
        uid: 'user@example.com',
        expiry: '9999999999',
        tokenType: 'Bearer',
      };

      fetchMock.mockResolvedValue(
        new Response('{}', {
          status: 200,
          headers: {
            'access-token': newTokens.accessToken,
            client: newTokens.client,
            uid: newTokens.uid,
            expiry: newTokens.expiry,
            'token-type': newTokens.tokenType,
          },
        })
      );

      await client.request('/test');

      expect(client.getTokens()).toEqual(newTokens);
    });

    it('should send JSON body', async () => {
      fetchMock.mockResolvedValue(new Response('{}', { status: 200 }));

      await client.request('/test', {
        method: 'POST',
        body: { foo: 'bar' },
      });

      const callArgs = fetchMock.mock.calls[0];
      expect(callArgs[1].body).toBe('{"foo":"bar"}');
      expect(callArgs[1].headers['Content-Type']).toBe('application/json');
    });

    it('should handle query params', async () => {
      fetchMock.mockResolvedValue(new Response('{}', { status: 200 }));

      await client.request('/test', {
        params: { foo: 'bar', baz: 'qux' },
      });

      const callArgs = fetchMock.mock.calls[0];
      expect(callArgs[0]).toBe('https://api.example.com/test?foo=bar&baz=qux');
    });

    it('should throw OgiriAuthError on 401', async () => {
      client.setTokens(mockTokens);
      const onAuthError = vi.fn();
      client = new OgiriClient({
        baseURL: 'https://api.example.com',
        onAuthError,
      });
      client.setTokens(mockTokens);

      fetchMock.mockResolvedValue(
        new Response('{"error":"Unauthorized"}', { status: 401 })
      );

      await expect(client.request('/protected')).rejects.toThrow(OgiriAuthError);

      expect(client.isAuthenticated()).toBe(false);
      expect(onAuthError).toHaveBeenCalled();
    });

    it('should throw generic error on non-401 failures', async () => {
      fetchMock.mockResolvedValue(
        new Response('Internal Server Error', { status: 500 })
      );

      await expect(client.request('/test')).rejects.toThrow('HTTP 500');
    });
  });

  describe('convenience methods', () => {
    beforeEach(() => {
      fetchMock.mockResolvedValue(new Response('{}', { status: 200 }));
    });

    it('should call GET', async () => {
      await client.get('/users');
      expect(fetchMock).toHaveBeenCalledWith(
        'https://api.example.com/users',
        expect.objectContaining({ method: 'GET' })
      );
    });

    it('should call POST', async () => {
      await client.post('/users', { name: 'John' });
      const callArgs = fetchMock.mock.calls[0];
      expect(callArgs[1].method).toBe('POST');
      expect(callArgs[1].body).toBe('{"name":"John"}');
    });

    it('should call PUT', async () => {
      await client.put('/users/1', { name: 'Jane' });
      const callArgs = fetchMock.mock.calls[0];
      expect(callArgs[1].method).toBe('PUT');
      expect(callArgs[1].body).toBe('{"name":"Jane"}');
    });

    it('should call DELETE', async () => {
      await client.delete('/users/1');
      expect(fetchMock).toHaveBeenCalledWith(
        'https://api.example.com/users/1',
        expect.objectContaining({ method: 'DELETE' })
      );
    });
  });

  describe('auth methods', () => {
    beforeEach(() => {
      fetchMock.mockResolvedValue(new Response('{}', { status: 200 }));
    });

    it('should support bearer auth method', async () => {
      client = new OgiriClient({
        baseURL: 'https://api.example.com',
        authMethod: 'bearer',
      });
      client.setTokens(mockTokens);

      await client.request('/test');

      const callArgs = fetchMock.mock.calls[0];
      const headers = callArgs[1].headers;
      expect(headers.Authorization).toMatch(/^Bearer /);
    });

    it('should support cookies auth method', async () => {
      client = new OgiriClient({
        baseURL: 'https://api.example.com',
        authMethod: 'cookies',
      });
      client.setTokens(mockTokens);

      await client.request('/test');

      const callArgs = fetchMock.mock.calls[0];
      expect(callArgs[1].credentials).toBe('include');
      expect(callArgs[1].headers.Cookie).toContain('access-token=test-token');
    });
  });
});
