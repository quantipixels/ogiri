import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, auth } from "./client";
import type { OgiriTokens } from "ogiri-security-client";

interface LoginRequest {
    username: string;
    password: string;
}

interface LoginResponse {
    "access-token": string;
    client: string;
    uid: string;
    expiry: string;
    "token-type": string;
}

interface User {
    id: string;
    username: string;
    email: string;
}

interface DemoInfo {
    message: string;
    timestamp: string;
}

export function useLogin() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (credentials: LoginRequest) => {
            const response = await api.post<LoginResponse>("/api/auth/login", credentials);
            return response.data;
        },
        onSuccess: (data) => {
            const tokens: OgiriTokens = {
                accessToken: data["access-token"],
                client: data.client,
                uid: data.uid,
                expiry: data.expiry,
                tokenType: data["token-type"],
            };
            auth.setTokens(tokens);
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

export function useProfile() {
    return useQuery<User>({
        queryKey: ["me"],
        queryFn: async () => {
            const response = await api.get<User>("/api/me");
            return response.data;
        },
        enabled: auth.isAuthenticated(),
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
