import { OgiriAuth, LocalStorageTokenStorage } from "../lib/auth";
import { createAxiosInterceptors } from "../lib/axios-ogiri";
import axios from "axios";

export const auth = new OgiriAuth({
    authMethod: "headers",
    storage: new LocalStorageTokenStorage(),
});

export const api = axios.create({ baseURL: "" });

const { request, response } = createAxiosInterceptors(auth);
api.interceptors.request.use(request);
api.interceptors.response.use(response.onFulfilled, response.onRejected);
