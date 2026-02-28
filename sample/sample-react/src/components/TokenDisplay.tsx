import { useEffect, useRef, useState } from "react";
import type { OgiriTokens } from "../lib/auth";

interface Props {
    tokens: OgiriTokens | null;
}

export function TokenDisplay({ tokens }: Props) {
    const prevTokenRef = useRef<string | null>(null);
    const [highlight, setHighlight] = useState(false);

    useEffect(() => {
        const current = tokens?.accessToken ?? null;
        if (prevTokenRef.current !== null && prevTokenRef.current !== current) {
            setHighlight(true);
            const timer = setTimeout(() => setHighlight(false), 1000);
            return () => clearTimeout(timer);
        }
        prevTokenRef.current = current;
    }, [tokens?.accessToken]);

    if (!tokens) return <p className="muted">No tokens</p>;

    return (
        <div className={`token-display ${highlight ? "rotated" : ""}`}>
            <div className="token-field">
                <span className="token-label">access-token</span>
                <code>{tokens.accessToken.substring(0, 8)}...</code>
            </div>
            <div className="token-field">
                <span className="token-label">client</span>
                <code>{tokens.client}</code>
            </div>
            <div className="token-field">
                <span className="token-label">expiry</span>
                <code>{tokens.expiry}</code>
            </div>
        </div>
    );
}
