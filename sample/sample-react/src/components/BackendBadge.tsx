const target = import.meta.env.VITE_API_TARGET as string | undefined;

function getBackend(): { label: string; color: string } {
    if (!target) return { label: "mock-server", color: "#6b7280" };
    if (target.includes("48081")) return { label: "kotlin-server", color: "#7c3aed" };
    if (target.includes("48080")) return { label: "java-server", color: "#b45309" };
    return { label: "live-server", color: "#059669" };
}

const backend = getBackend();

export function BackendBadge() {
    return (
        <span
            style={{
                display: "inline-block",
                padding: "0.2rem 0.55rem",
                borderRadius: "999px",
                background: backend.color,
                color: "#fff",
                fontSize: "0.72rem",
                fontWeight: 600,
                letterSpacing: "0.04em",
                userSelect: "none",
            }}
        >
            {backend.label}
        </span>
    );
}
