import { useState, type FormEvent } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { useLogin } from "../api/queries";

export function LoginPage() {
    const { isAuthenticated } = useAuth();
    const navigate = useNavigate();
    const login = useLogin();
    const [username, setUsername] = useState("user1@example.com");
    const [password, setPassword] = useState("password");

    if (isAuthenticated) {
        return <Navigate to="/dashboard" replace />;
    }

    const handleSubmit = (e: FormEvent) => {
        e.preventDefault();
        login.mutate(
            { username, password },
            {
                onSuccess: () => navigate("/dashboard"),
            },
        );
    };

    return (
        <div className="page">
            <div className="card">
                <h1>Ogiri Auth Demo</h1>
                <p>Login to see token rotation in action</p>
                <form onSubmit={handleSubmit}>
                    <div className="field">
                        <label htmlFor="username">Email</label>
                        <input
                            id="username"
                            type="text"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            placeholder="user1@example.com"
                            required
                        />
                    </div>
                    <div className="field">
                        <label htmlFor="password">Password</label>
                        <input
                            id="password"
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="password"
                            required
                        />
                    </div>
                    {login.error && <div className="error">{login.error.message}</div>}
                    <button type="submit" disabled={login.isPending}>
                        {login.isPending ? "Logging in..." : "Login"}
                    </button>
                </form>
                <p className="hint">Try user1@example.com / password or user2@example.com / password</p>
            </div>
        </div>
    );
}
