'use client';

import { createContext, useContext, useState, useEffect, ReactNode } from 'react';

interface AuthUser {
  email: string;
  id: number;
  coins: number;
}

interface AuthState {
  authenticated: boolean;
  user?: AuthUser;
}

interface AuthContextType {
  authState: AuthState;
  coinsLoading: boolean;
  checkAuthenticationStatus: () => Promise<void>;
  handleLogout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authState, setAuthState] = useState<AuthState>({ authenticated: false });
  const [coinsLoading, setCoinsLoading] = useState(true);

  const checkAuthenticationStatus = async () => {
    try {
      const response = await fetch("/api/auth/check", {
        credentials: "include",
      });
      const data = await response.json();
      setAuthState(data);
    } catch (error) {
      console.error("Error checking auth status:", error);
      setAuthState({ authenticated: false });
    } finally {
      setCoinsLoading(false);
    }
  };

  useEffect(() => {
    checkAuthenticationStatus();

    const handleVisibilityChange = () => {
      if (!document.hidden) {
        checkAuthenticationStatus();
      }
    };

    const refreshInterval = setInterval(() => {
      checkAuthenticationStatus();
    }, 300000);

    window.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      window.removeEventListener('visibilitychange', handleVisibilityChange);
      clearInterval(refreshInterval);
    };
  }, []);

  const handleLogout = async () => {
    try {
      const response = await fetch("/api/auth/logout", {
        method: "POST",
        credentials: "include",
      });
      if (response.ok) {
        setAuthState({ authenticated: false });
        window.location.reload();
      }
    } catch (error) {
      console.error("Error during logout:", error);
    }
  };

  return (
    <AuthContext.Provider value={{ authState, coinsLoading, checkAuthenticationStatus, handleLogout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
