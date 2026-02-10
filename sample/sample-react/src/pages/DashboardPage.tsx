import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { useProfile, useDemoInfo, useLogout } from "../api/queries";
import { TokenDisplay } from "../components/TokenDisplay";

export function DashboardPage() {
    const { tokens } = useAuth();
    const navigate = useNavigate();
    const profile = useProfile();
    const demoInfo = useDemoInfo();
    const logout = useLogout();

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
                    <button onClick={handleLogout} className="btn-secondary">
                        Logout
                    </button>
                </div>

                <section>
                    <h2>Profile</h2>
                    {profile.isLoading && <p>Loading...</p>}
                    {profile.data && (
                        <dl>
                            <dt>Username</dt>
                            <dd>{profile.data.username}</dd>
                            <dt>Email</dt>
                            <dd>{profile.data.email}</dd>
                            <dt>ID</dt>
                            <dd>{profile.data.id}</dd>
                        </dl>
                    )}
                </section>

                <section>
                    <h2>Token Rotation Demo</h2>
                    <TokenDisplay tokens={tokens} />
                    <button onClick={() => demoInfo.refetch()} disabled={demoInfo.isFetching}>
                        {demoInfo.isFetching ? "Requesting..." : "Make Request"}
                    </button>
                    {demoInfo.data && <pre className="response">{JSON.stringify(demoInfo.data, null, 2)}</pre>}
                </section>
            </div>
        </div>
    );
}
