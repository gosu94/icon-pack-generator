'use client';

import React, { useState, useEffect, useRef } from "react";
import Link from "next/link";
import Image from "next/image";
import {
    Image as ImageIcon,
    LogOut,
    MessageSquare,
    Paintbrush,
    Settings,
    Store,
    User,
    X,
    Sparkles,
    Menu,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";

interface NavigationProps {}

const Navigation: React.FC<NavigationProps> = () => {
  const { authState, coinsLoading, handleLogout } = useAuth();
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const mobileMenuRef = useRef<HTMLDivElement>(null);

  const openLoginModal = () => {
    setIsLoginModalOpen(true);
    setTimeout(() => setIsModalVisible(true), 10);
  };

  const closeLoginModal = () => {
    setIsModalVisible(false);
    setTimeout(() => setIsLoginModalOpen(false), 300);
  };

  const handleGoogleLogin = () => {
    window.location.href = "/oauth2/authorization/google";
  };

  const toggleMobileMenu = () => {
    setIsMobileMenuOpen(!isMobileMenuOpen);
  };

  const displayCoins =
    authState.authenticated && authState.user ? authState.user.coins : 0;

  // Handle clicking outside mobile menu to close it
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (mobileMenuRef.current && !mobileMenuRef.current.contains(event.target as Node)) {
        setIsMobileMenuOpen(false);
      }
    };

    const handleResize = () => {
      setIsMobileMenuOpen(false);
    };

    if (isMobileMenuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      window.addEventListener('resize', handleResize);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      window.removeEventListener('resize', handleResize);
    };
  }, [isMobileMenuOpen]);

  return (
    <nav className="border-b border-gray-200 px-6 py-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-6">
          <div className="flex items-center space-x-3">
            <Image
              src="/images/logo small.webp"
              alt="Icon Pack Generator"
              width={32}
              height={32}
            />
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
              {/* Coin Balance Display - always visible for authenticated users */}
              <div className="flex items-center space-x-2 bg-yellow-50 border border-yellow-200 rounded-lg px-3 py-2">
                <Image
                  src="/images/coin.webp"
                  alt="Coins"
                  width={20}
                  height={20}
                />
                <span className="text-sm font-semibold text-yellow-800">
                  {coinsLoading ? "..." : displayCoins}
                </span>
              </div>

              {/* Desktop Navigation - hidden on mobile */}
              <div className="hidden md:flex items-center space-x-4">
                <Link href="/dashboard" className="p-2 hover:bg-gray-100 rounded-lg">
                  <Sparkles className="w-5 h-5 text-gray-700" />
                </Link>
                <Link
                  href="/gallery"
                  className="p-2 hover:bg-gray-100 rounded-lg"
                >
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
                <Link href="/feedback" className="p-2 hover:bg-gray-100 rounded-lg">
                  <MessageSquare className="w-5 h-5 text-gray-700" />
                </Link>
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
              </div>

              {/* Mobile Menu Button - visible only on mobile */}
              <div className="md:hidden relative" ref={mobileMenuRef}>
                <button
                  onClick={toggleMobileMenu}
                  className="p-2 hover:bg-gray-100 rounded-lg"
                  aria-label="Toggle mobile menu"
                >
                  <Menu className="w-5 h-5 text-gray-700" />
                </button>

                {/* Mobile Dropdown Menu */}
                {isMobileMenuOpen && (
                  <div className="absolute right-0 top-12 w-48 bg-white border border-gray-200 rounded-lg shadow-lg z-50">
                    <div className="py-2">
                      <Link 
                        href="/dashboard" 
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100"
                        onClick={() => setIsMobileMenuOpen(false)}
                      >
                        <Sparkles className="w-4 h-4 text-gray-700" />
                        <span className="text-sm font-medium text-gray-700">Dashboard</span>
                      </Link>
                      <Link 
                        href="/gallery" 
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100"
                        onClick={() => setIsMobileMenuOpen(false)}
                      >
                        <ImageIcon className="w-4 h-4 text-gray-700" />
                        <span className="text-sm font-medium text-gray-700">Gallery</span>
                      </Link>
                      <Link 
                        href="/background-remover" 
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100"
                        onClick={() => setIsMobileMenuOpen(false)}
                      >
                        <Paintbrush className="w-4 h-4 text-gray-700" />
                        <span className="text-sm font-medium text-gray-700">Background Remover</span>
                      </Link>
                      <Link 
                        href="/store" 
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100"
                        onClick={() => setIsMobileMenuOpen(false)}
                      >
                        <Store className="w-4 h-4 text-gray-700" />
                        <span className="text-sm font-medium text-gray-700">Store</span>
                      </Link>
                      <Link 
                        href="/feedback" 
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100"
                        onClick={() => setIsMobileMenuOpen(false)}
                      >
                        <MessageSquare className="w-4 h-4 text-gray-700" />
                        <span className="text-sm font-medium text-gray-700">Feedback</span>
                      </Link>
                      <button 
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100 w-full text-left"
                        onClick={() => setIsMobileMenuOpen(false)}
                      >
                        <Settings className="w-4 h-4 text-gray-700" />
                        <span className="text-sm font-medium text-gray-700">Settings</span>
                      </button>
                      <div className="border-t border-gray-100 my-2"></div>
                      <button
                        onClick={() => {
                          handleLogout();
                          setIsMobileMenuOpen(false);
                        }}
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100 w-full text-left"
                      >
                        <LogOut className="w-4 h-4 text-red-600" />
                        <span className="text-sm font-medium text-red-600">Logout</span>
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </>
          ) : (
            <>
              {/* Sign In Button for unauthenticated users */}
              <button
                onClick={openLoginModal}
                className="flex items-center space-x-2 px-4 py-2 rounded-lg text-white font-semibold bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 transform hover:scale-[1.02] shadow-lg hover:shadow-xl transition-all duration-200"
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
        <div
          className={`fixed inset-0 bg-black flex items-center justify-center z-50 transition-opacity duration-300 ${
            isModalVisible ? "bg-opacity-50" : "bg-opacity-0"
          }`}
        >
          <div
            className={`relative bg-white/80 backdrop-blur-md rounded-3xl p-8 shadow-2xl border-2 border-purple-200/50 w-96 max-w-md mx-4 transition-all duration-300 ${
              isModalVisible ? "opacity-100 scale-100" : "opacity-0 scale-95"
            }`}
          >
            <div className="absolute inset-0 rounded-3xl bg-gradient-to-br from-white/30 to-transparent pointer-events-none"></div>
            <div className="relative z-10">
              <div className="flex justify-between items-center mb-6">
                <h2 className="text-2xl font-bold text-slate-900">Sign In</h2>
                <button
                  onClick={closeLoginModal}
                  className="text-slate-400 hover:text-slate-600"
                >
                  <X className="w-6 h-6" />
                </button>
              </div>

              <div className="space-y-6">
                <p className="text-slate-600 text-center">
                  Sign in to access your account and generate custom icons
                </p>

                <button
                  onClick={handleGoogleLogin}
                  className="w-full flex items-center justify-center space-x-3 bg-white border border-gray-200 hover:bg-gray-50 text-gray-800 px-4 py-3 rounded-xl shadow-md hover:shadow-lg transition-all duration-200"
                >
                  <svg className="w-5 h-5" viewBox="0 0 24 24">
                    <path
                      fill="#4285F4"
                      d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                    />
                    <path
                      fill="#34A853"
                      d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                    />
                    <path
                      fill="#FBBC05"
                      d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                    />
                    <path
                      fill="#EA4335"
                      d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                    />
                  </svg>
                  <span className="font-medium">Continue with Google</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </nav>
  );
};

export default Navigation;