import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, auth } from "./client";

interface LoginRequest {
    username: string;
    password: string;
}

interface LoginResponse {
    accessToken: string;
    client: string;
    uid: string;
    expiry: string;
    message: string;
}

interface DemoInfo {
    authenticated: boolean;
    principal: string;
    authorities: string[];
    authMethod: string;
    message: string;
}

export function useLogin() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (credentials: LoginRequest) => {
            const response = await api.post<LoginResponse>("/api/auth/login", credentials);
            return response.data;
        },
        onSuccess: () => {
            // Tokens are extracted from response headers by the axios interceptor.
            // No need to call auth.setTokens() here — doing so would fire a
            // redundant notify() causing an extra React re-render.
            queryClient.invalidateQueries();
        },
    });
}

export function useLogout() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async () => {
            await api.post("/api/auth/logout");
        },
        onSuccess: () => {
            auth.clearTokens();
            queryClient.invalidateQueries();
        },
    });
}

export function useExpireToken() {
    return useMutation({
        mutationFn: async () => {
            await api.post("/api/test/expire-token");
        },
    });
}

export function useDemoInfo() {
    return useQuery<DemoInfo>({
        queryKey: ["demo-info"],
        queryFn: async () => {
            const response = await api.get<DemoInfo>("/api/demo/info");
            return response.data;
        },
        enabled: auth.isAuthenticated(),
    });
}
