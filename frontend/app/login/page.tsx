'use client';

import React, { useState, useEffect, Suspense } from "react";
import Link from "next/link";
import Image from "next/image";
import { useSearchParams } from 'next/navigation';
import {
    Mail,
    Eye,
    EyeOff,
    ArrowLeft,
    CheckCircle,
    AlertCircle,
    Sparkles,
} from "lucide-react";
import { useAuth } from "../../context/AuthContext";

function LoginPageContent() {
  const { authState, checkAuthenticationStatus } = useAuth();
  const searchParams = useSearchParams();
  const redirect = searchParams.get('redirect') || '/dashboard';

  // Email/Password Authentication States
  const [loginStep, setLoginStep] = useState<'method' | 'email' | 'password' | 'loading' | 'success' | 'email-sent'>('method');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [authError, setAuthError] = useState('');
  const [isEmailLoading, setIsEmailLoading] = useState(false);
  const [isPasswordLoading, setIsPasswordLoading] = useState(false);
  const [emailExists, setEmailExists] = useState(false);

  // Redirect if already authenticated
  useEffect(() => {
    if (authState.authenticated) {
      window.location.href = redirect;
    }
  }, [authState.authenticated, redirect]);

  const handleGoogleLogin = () => {
    // Store redirect URL in session storage so backend can use it after OAuth
    sessionStorage.setItem('loginRedirect', redirect);
    window.location.href = "/oauth2/authorization/google";
  };

  const handleEmailContinue = () => {
    setAuthError('');
    setLoginStep('email');
  };

  const handleBackToMethod = () => {
    setLoginStep('method');
    setAuthError('');
  };

  const handleEmailSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsEmailLoading(true);
    setAuthError('');

    try {
      const response = await fetch('/api/auth/check-email', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email }),
        credentials: 'include',
      });

      const data = await response.json();

      if (response.ok) {
        setEmailExists(data.exists);
        
        if (data.exists && data.hasPassword) {
          // User exists with password, go to password step
          setLoginStep('password');
        } else {
          // User doesn't exist or no password set, send setup email
          const emailResponse = await fetch('/api/auth/send-setup-email', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({ email }),
            credentials: 'include',
          });

          if (emailResponse.ok) {
            setLoginStep('email-sent');
          } else {
            setAuthError('Failed to send setup email. Please try again.');
          }
        }
      } else {
        setAuthError('An error occurred. Please try again.');
      }
    } catch (error) {
      console.error('Error checking email:', error);
      setAuthError('Network error. Please check your connection.');
    } finally {
      setIsEmailLoading(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsPasswordLoading(true);
    setAuthError('');

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, password, rememberMe }),
        credentials: 'include',
      });

      const data = await response.json();

      if (response.ok && data.success) {
        setLoginStep('success');
        // Refresh auth state
        await checkAuthenticationStatus();
        
        // Redirect after short delay
        setTimeout(() => {
          window.location.href = redirect;
        }, 1500);
      } else {
        setAuthError(data.message || 'Invalid email or password');
      }
    } catch (error) {
      console.error('Error logging in:', error);
      setAuthError('Network error. Please check your connection.');
    } finally {
      setIsPasswordLoading(false);
    }
  };

  const handleForgotPassword = async () => {
    setIsPasswordLoading(true);
    setAuthError('');

    try {
      const response = await fetch('/api/auth/send-reset-email', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email }),
        credentials: 'include',
      });

      if (response.ok) {
        setLoginStep('email-sent');
      } else {
        setAuthError('Failed to send reset email. Please try again.');
      }
    } catch (error) {
      console.error('Error sending reset email:', error);
      setAuthError('Network error. Please check your connection.');
    } finally {
      setIsPasswordLoading(false);
    }
  };

  // Show loading while checking auth state
  if (authState.authenticated) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-purple-50 via-blue-50 to-indigo-100">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Redirecting...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-br from-purple-50 via-blue-50 to-indigo-100 p-4">
      {/* Header */}
      <div className="w-full max-w-md mb-8">
        <Link href="/" className="flex items-center space-x-3 justify-center group">
          <Image
            src="/images/logo small.webp"
            alt="Icon Pack Generator"
            width={40}
            height={40}
            className="transition-transform group-hover:scale-105"
          />
          <span className="text-2xl font-medium text-slate-900 cursor-pointer flex items-center">
            Icon Pack Generator
            <span className="ml-2 font-bold bg-clip-text text-transparent bg-gradient-to-b from-purple-400 to-purple-700">
              AI
            </span>
            <Sparkles className="w-4 h-4 text-purple-400 ml-1" />
          </span>
        </Link>
      </div>

      {/* Login Card */}
      <div className="relative bg-white/80 backdrop-blur-md rounded-3xl p-8 shadow-2xl border-2 border-purple-200/50 w-full max-w-md">
        <div className="absolute inset-0 rounded-3xl bg-gradient-to-br from-white/30 to-transparent pointer-events-none"></div>
        <div className="relative z-10">
          <div className="flex justify-between items-center mb-6">
            <h1 className="text-2xl font-bold text-slate-900">
              {loginStep === 'method' && 'Sign In'}
              {loginStep === 'email' && 'Enter Email'}
              {loginStep === 'password' && 'Enter Password'}
              {loginStep === 'success' && 'Welcome Back!'}
              {loginStep === 'email-sent' && 'Check Your Email'}
            </h1>
            <div className="flex items-center space-x-2">
              {(loginStep === 'email' || loginStep === 'password') && (
                <button
                  onClick={handleBackToMethod}
                  className="text-slate-400 hover:text-slate-600 transition-colors"
                >
                  <ArrowLeft className="w-5 h-5" />
                </button>
              )}
            </div>
          </div>

          {/* Error Message */}
          {authError && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg">
              <div className="flex items-center">
                <AlertCircle className="w-4 h-4 text-red-500 mr-2" />
                <p className="text-sm text-red-800">{authError}</p>
              </div>
            </div>
          )}

          {/* Method Selection Step */}
          {loginStep === 'method' && (
            <div className="space-y-4">
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
              <button
                onClick={handleEmailContinue}
                className="w-full flex items-center justify-center space-x-3 bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 text-white px-4 py-3 rounded-xl shadow-md hover:shadow-lg transition-all duration-200"
              >
                <Mail className="w-5 h-5" />
                <span className="font-medium">Continue with Email</span>
              </button>
            </div>
          )}

          {/* Email Input Step */}
          {loginStep === 'email' && (
            <div className="space-y-4">
              <p className="text-slate-600 text-center">
                Enter your email address to continue
              </p>

              <form onSubmit={handleEmailSubmit} className="space-y-4">
                <div>
                  <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">
                    Email Address
                  </label>
                  <input
                    id="email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className="w-full px-4 py-3 border border-gray-300 rounded-xl focus:ring-2 focus:ring-purple-500 focus:border-transparent transition-all"
                    placeholder="your@email.com"
                    required
                    disabled={isEmailLoading}
                    autoFocus
                  />
                </div>

                <button
                  type="submit"
                  disabled={isEmailLoading || !email.trim()}
                  className="w-full bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed text-white font-semibold py-3 px-6 rounded-xl transition-all duration-200"
                >
                  {isEmailLoading ? (
                    <div className="flex items-center justify-center">
                      <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white mr-2"></div>
                      Checking...
                    </div>
                  ) : (
                    'Continue'
                  )}
                </button>
              </form>
            </div>
          )}

          {/* Password Input Step */}
          {loginStep === 'password' && (
            <div className="space-y-4">
              <p className="text-slate-600 text-center">
                Welcome back! Enter your password for <strong>{email}</strong>
              </p>

              <form onSubmit={handlePasswordSubmit} className="space-y-4">
                <div>
                  <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
                    Password
                  </label>
                  <div className="relative">
                    <input
                      id="password"
                      type={showPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className="w-full px-4 py-3 border border-gray-300 rounded-xl focus:ring-2 focus:ring-purple-500 focus:border-transparent pr-12 transition-all"
                      placeholder="Enter your password"
                      required
                      disabled={isPasswordLoading}
                      autoFocus
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute inset-y-0 right-0 pr-3 flex items-center"
                      disabled={isPasswordLoading}
                    >
                      {showPassword ? (
                        <EyeOff className="w-5 h-5 text-gray-400" />
                      ) : (
                        <Eye className="w-5 h-5 text-gray-400" />
                      )}
                    </button>
                  </div>
                </div>

                <div className="flex items-center">
                  <input
                    id="remember-me"
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                    className="h-4 w-4 text-purple-600 focus:ring-purple-500 border-gray-300 rounded"
                    disabled={isPasswordLoading}
                  />
                  <label htmlFor="remember-me" className="ml-2 block text-sm text-gray-700">
                    Keep me signed in
                  </label>
                </div>

                <button
                  type="submit"
                  disabled={isPasswordLoading || !password.trim()}
                  className="w-full bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed text-white font-semibold py-3 px-6 rounded-xl transition-all duration-200"
                >
                  {isPasswordLoading ? (
                    <div className="flex items-center justify-center">
                      <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white mr-2"></div>
                      Signing In...
                    </div>
                  ) : (
                    'Sign In'
                  )}
                </button>

                <button
                  type="button"
                  onClick={handleForgotPassword}
                  className="w-full text-purple-600 hover:text-purple-700 text-sm font-medium transition-colors"
                  disabled={isPasswordLoading}
                >
                  Forgot your password?
                </button>
              </form>
            </div>
          )}

          {/* Success Step */}
          {loginStep === 'success' && (
            <div className="text-center space-y-4">
              <CheckCircle className="w-16 h-16 text-green-500 mx-auto" />
              <p className="text-slate-600">
                Successfully signed in! Redirecting...
              </p>
            </div>
          )}

          {/* Email Sent Step */}
          {loginStep === 'email-sent' && (
            <div className="text-center space-y-4">
              <div className="w-16 h-16 bg-purple-100 rounded-full flex items-center justify-center mx-auto">
                <Mail className="w-8 h-8 text-purple-600" />
              </div>
              <div className="space-y-2">
                <p className="text-slate-600">
                  We've sent you an email with instructions to {emailExists ? 'reset your password' : 'set up your account'}.
                </p>
                <p className="text-sm text-slate-500">
                  Check your inbox at <strong>{email}</strong>
                </p>
              </div>
              <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                <p className="text-sm text-blue-800">
                  💡 Don't see the email? Check your spam folder or try again in a few minutes.
                </p>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Footer */}
      <div className="mt-8 text-center space-y-2">
        <p className="text-sm text-slate-500">
          Don't have an account? Sign up by entering your email above.
        </p>
        <Link 
          href="/" 
          className="text-sm text-purple-600 hover:text-purple-700 transition-colors"
        >
          ← Back to Home
        </Link>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-purple-50 via-blue-50 to-indigo-100">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Loading...</p>
        </div>
      </div>
    }>
      <LoginPageContent />
    </Suspense>
  );
}
