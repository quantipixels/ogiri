import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { useDemoInfo, useExpireToken, useLogout } from "../api/queries";
import { TokenDisplay } from "../components/TokenDisplay";
import { BackendBadge } from "../components/BackendBadge";

export function DashboardPage() {
    const { tokens } = useAuth();
    const navigate = useNavigate();
    const demoInfo = useDemoInfo();
    const logout = useLogout();
    const expireToken = useExpireToken();

    const handleLogout = () => {
        logout.mutate(undefined, {
            onSuccess: () => navigate("/login"),
        });
    };

    return (
        <div className="page">
            <div className="card">
                <div className="header-row">
                    <h1>Dashboard</h1>
                    <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
                        <BackendBadge />
                        <button onClick={handleLogout} className="btn-secondary">
                            Logout
                        </button>
                    </div>
                </div>

                {demoInfo.data && (
                    <section>
                        <h2>Session</h2>
                        <dl>
                            <dt>Principal</dt>
                            <dd>{demoInfo.data.principal}</dd>
                            <dt>Auth Method</dt>
                            <dd>{demoInfo.data.authMethod}</dd>
                            <dt>Authorities</dt>
                            <dd>{demoInfo.data.authorities.join(", ")}</dd>
                        </dl>
                    </section>
                )}

                <section>
                    <h2>Token Rotation Demo</h2>
                    <TokenDisplay tokens={tokens} />
                    <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                        <button onClick={() => demoInfo.refetch()} disabled={demoInfo.isFetching}>
                            {demoInfo.isFetching ? "Requesting..." : "Make Request"}
                        </button>
                        <button
                            className="btn-secondary"
                            onClick={() => expireToken.mutate()}
                            disabled={expireToken.isPending || !tokens}
                            title="Backdates the token expiry so the next request returns 401"
                        >
                            {expireToken.isPending ? "Expiring..." : "Expire Token"}
                        </button>
                    </div>
                    {demoInfo.data && <pre className="response">{JSON.stringify(demoInfo.data, null, 2)}</pre>}
                </section>
            </div>
        </div>
    );
}
