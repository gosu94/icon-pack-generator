import React, { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import {
    Home,
    Image as ImageIcon,
    LogOut,
    Menu,
    MessageSquare,
    Paintbrush,
    Settings,
    Store,
    User,
} from "lucide-react";

interface NavigationProps {
  coins: number;
  coinsLoading: boolean;
}

interface AuthUser {
  email: string;
  id: number;
  coins: number;
}

interface AuthState {
  authenticated: boolean;
  user?: AuthUser;
}

const Navigation: React.FC<NavigationProps> = ({ coins, coinsLoading }) => {
  const [authState, setAuthState] = useState<AuthState>({ authenticated: false });
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

  useEffect(() => {
    checkAuthenticationStatus();
  }, []);

  const checkAuthenticationStatus = async () => {
    try {
      const response = await fetch('/api/auth/check', {
        credentials: 'include',
      });
      const data = await response.json();
      setAuthState(data);
    } catch (error) {
      console.error('Error checking auth status:', error);
      setAuthState({ authenticated: false });
    }
  };

  const handleLogout = async () => {
    try {
      const response = await fetch('/api/auth/logout', {
        method: 'POST',
        credentials: 'include',
      });
      if (response.ok) {
        setAuthState({ authenticated: false });
        window.location.reload(); // Reload to clear any cached data
      }
    } catch (error) {
      console.error('Error during logout:', error);
    }
  };

  const handleGoogleLogin = () => {
    window.location.href = '/oauth2/authorization/google';
  };

  const displayCoins = authState.authenticated && authState.user ? authState.user.coins : coins;

  return (
    <nav className="border-b border-gray-200 px-6 py-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-6">
          <div className="flex items-center space-x-3">
            <Image src="/images/logo small.webp" alt="Icon Pack Generator" width={32} height={32} />
            <Link href="/">
              <span className="text-xl font-medium text-black cursor-pointer">
                Icon Pack Generator
              </span>
            </Link>
          </div>
        </div>

        <div className="flex items-center space-x-4">
          {authState.authenticated ? (
            <>
              {/* Coin Balance Display - only for authenticated users */}
              <div className="flex items-center space-x-2 bg-yellow-50 border border-yellow-200 rounded-lg px-3 py-2">
                <Image src="/images/coin.webp" alt="Coins" width={20} height={20} />
                <span className="text-sm font-semibold text-yellow-800">
                  {coinsLoading ? "..." : displayCoins}
                </span>
              </div>
              
              <Link href="/" className="p-2 hover:bg-gray-100 rounded-lg">
                <Menu className="w-5 h-5 text-gray-700" />
              </Link>
              <Link href="/gallery" className="p-2 hover:bg-gray-100 rounded-lg">
                <ImageIcon className="w-5 h-5 text-gray-700" />
              </Link>
              <Link
                href="/background-remover"
                className="p-2 hover:bg-gray-100 rounded-lg"
              >
                <Paintbrush className="w-5 h-5 text-gray-700" />
              </Link>
              <Link href="/store" className="p-2 hover:bg-gray-100 rounded-lg">
                <Store className="w-5 h-5 text-gray-700" />
              </Link>
              <button className="p-2 hover:bg-gray-100 rounded-lg">
                <MessageSquare className="w-5 h-5 text-gray-700" />
              </button>
              <button className="p-2 hover:bg-gray-100 rounded-lg">
                <Settings className="w-5 h-5 text-gray-700" />
              </button>
              <button 
                onClick={handleLogout}
                className="p-2 hover:bg-gray-100 rounded-lg"
                title="Logout"
              >
                <LogOut className="w-5 h-5 text-gray-700" />
              </button>
            </>
          ) : (
            <>
              {/* Sign In Button for unauthenticated users */}
              <button
                onClick={() => setIsLoginModalOpen(true)}
                className="flex items-center space-x-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg transition-colors"
              >
                <User className="w-4 h-4" />
                <span className="text-sm font-medium">Sign In</span>
              </button>
            </>
          )}
        </div>
      </div>
      
      {/* Login Modal */}
      {isLoginModalOpen && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-96 max-w-md mx-4">
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-xl font-semibold text-gray-900">Sign In</h2>
              <button
                onClick={() => setIsLoginModalOpen(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            
            <div className="space-y-4">
              <p className="text-gray-600 text-center">
                Sign in to access your account and generate custom icons
              </p>
              
              <button
                onClick={handleGoogleLogin}
                className="w-full flex items-center justify-center space-x-3 bg-white border border-gray-300 hover:bg-gray-50 text-gray-700 px-4 py-3 rounded-lg transition-colors"
              >
                <svg className="w-5 h-5" viewBox="0 0 24 24">
                  <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                  <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                  <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                  <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                </svg>
                <span className="font-medium">Continue with Google</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </nav>
  );
};

export default Navigation;
