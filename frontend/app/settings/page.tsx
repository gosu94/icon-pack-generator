'use client';

import React, { useState, useEffect, Suspense } from 'react';
import { useRouter } from 'next/navigation';
import {
  Eye, 
  EyeOff, 
  CheckCircle, 
  AlertCircle, 
  Settings, 
  User, 
  Shield,
  ArrowLeft,
  ExternalLink
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import Navigation from '../../components/Navigation';

function SettingsContent() {
  const { authState } = useAuth();
  const router = useRouter();
  
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  // Redirect if not authenticated
  useEffect(() => {
    if (!authState.authenticated) {
      router.push('/');
    }
  }, [authState.authenticated, router]);

  if (!authState.authenticated || !authState.user) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-purple-50 to-blue-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-3xl shadow-2xl border border-purple-200/50 p-8 w-full max-w-md">
          <div className="text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-600 mx-auto mb-4"></div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">Loading...</h2>
            <p className="text-gray-600">Checking authentication...</p>
          </div>
        </div>
      </div>
    );
  }

  const handlePasswordChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess(false);

    if (newPassword.length < 8) {
      setError('New password must be at least 8 characters long');
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('New passwords do not match');
      return;
    }

    if (currentPassword === newPassword) {
      setError('New password must be different from current password');
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch('/api/auth/change-password', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          currentPassword,
          newPassword,
          confirmPassword,
        }),
        credentials: 'include',
      });

      if (response.ok) {
        setSuccess(true);
        setCurrentPassword('');
        setNewPassword('');
        setConfirmPassword('');
        
        // Show success message for 3 seconds
        setTimeout(() => {
          setSuccess(false);
        }, 3000);
      } else {
        setError('Failed to change password. Please check your current password and try again.');
      }
    } catch (error) {
      console.error('Error changing password:', error);
      setError('Network error. Please check your connection and try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const getPasswordStrength = (pwd: string) => {
    if (pwd.length === 0) return { strength: 0, text: '', color: '' };
    if (pwd.length < 8) return { strength: 1, text: 'Too short', color: 'text-red-500' };
    
    let score = 0;
    if (pwd.length >= 8) score++;
    if (/[a-z]/.test(pwd)) score++;
    if (/[A-Z]/.test(pwd)) score++;
    if (/[0-9]/.test(pwd)) score++;
    if (/[^A-Za-z0-9]/.test(pwd)) score++;

    if (score <= 2) return { strength: 2, text: 'Weak', color: 'text-orange-500' };
    if (score <= 4) return { strength: 3, text: 'Good', color: 'text-yellow-500' };
    return { strength: 4, text: 'Strong', color: 'text-green-500' };
  };

  const passwordStrength = getPasswordStrength(newPassword);

  // Check if user has email authentication (not OAuth)
  const canChangePassword = (authState.user as any)?.authProvider === 'EMAIL';
  const authProvider = (authState.user as any)?.authProvider;

  // Get provider display name and management URL
  const getProviderInfo = (provider: string) => {
    switch (provider) {
      case 'GOOGLE':
        return {
          name: 'Google',
          url: 'https://myaccount.google.com/security',
          icon: '🔐'
        };
      case 'FACEBOOK':
        return {
          name: 'Facebook',
          url: 'https://www.facebook.com/settings?tab=security',
          icon: '🔐'
        };
      case 'GITHUB':
        return {
          name: 'GitHub',
          url: 'https://github.com/settings/security',
          icon: '🔐'
        };
      case 'TWITTER':
        return {
          name: 'Twitter/X',
          url: 'https://twitter.com/settings/password',
          icon: '🔐'
        };
      default:
        return {
          name: provider.toLowerCase(),
          url: null,
          icon: '🔐'
        };
    }
  };

  const providerInfo = getProviderInfo(authProvider || '');

  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-50 to-blue-50">
      <Navigation useLoginPage={true} />
      <div className="max-w-4xl mx-auto p-4">
        {/* Header */}
        <div className="mb-8">
          <button
            onClick={() => router.back()}
            className="flex items-center text-purple-600 hover:text-purple-700 mb-4"
          >
            <ArrowLeft className="w-5 h-5 mr-2" />
            Back
          </button>
          
          <div className="bg-white rounded-3xl shadow-2xl border border-purple-200/50 p-6">
            <div className="flex items-center space-x-4">
              <div className="w-16 h-16 bg-gradient-to-br from-purple-600 to-blue-600 rounded-2xl flex items-center justify-center">
                <Settings className="w-8 h-8 text-white" />
              </div>
              <div>
                <h1 className="text-3xl font-bold text-gray-900">Account Settings</h1>
                <p className="text-gray-600 mt-1">Manage your account preferences and security</p>
              </div>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Sidebar */}
          <div className="lg:col-span-1">
            <div className="bg-white rounded-2xl shadow-lg border border-purple-200/30 p-6 sticky top-8">
              <div className="space-y-4">
                <div className="flex items-center space-x-3 text-purple-600 bg-purple-50 p-3 rounded-lg">
                  <Shield className="w-5 h-5" />
                  <span className="font-medium">Security</span>
                </div>
                
                <div className="flex items-center space-x-3 text-gray-600 p-3 rounded-lg hover:bg-gray-50 opacity-50 cursor-not-allowed">
                  <User className="w-5 h-5" />
                  <span className="font-medium">Profile</span>
                  <span className="text-xs bg-gray-200 px-2 py-1 rounded-full">Soon</span>
                </div>
              </div>
            </div>
          </div>

          {/* Main Content */}
          <div className="lg:col-span-2">
            <div className="bg-white rounded-2xl shadow-lg border border-purple-200/30 p-8">
              {/* Account Info */}
              <div className="mb-8 pb-8 border-b border-gray-200">
                <h2 className="text-xl font-semibold text-gray-900 mb-4">Account Information</h2>
                <div className="space-y-3">
                  <div>
                    <span className="text-sm text-gray-600">Email Address</span>
                    <p className="font-medium text-gray-900">{authState.user?.email}</p>
                  </div>
                  <div>
                    <span className="text-sm text-gray-600">Authentication Provider</span>
                    <div className="flex items-center space-x-2">
                      <span className="text-lg">{providerInfo.icon}</span>
                      <p className="font-medium text-gray-900">{providerInfo.name}</p>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <span className="text-sm text-gray-600">Regular Coins</span>
                      <p className="font-medium text-gray-900">{authState.user?.coins || 0}</p>
                    </div>
                    <div>
                      <span className="text-sm text-gray-600">Trial Coins</span>
                      <p className="font-medium text-gray-900">{authState.user?.trialCoins || 0}</p>
                    </div>
                  </div>
                </div>
              </div>

              {/* Change Password Section */}
              <div>
                <h2 className="text-xl font-semibold text-gray-900 mb-4">Password Management</h2>
                
                {!canChangePassword ? (
                  <div className="bg-blue-50 border border-blue-200 rounded-xl p-6">
                    <div className="flex items-start space-x-3">
                      <div className="flex-shrink-0">
                        <AlertCircle className="w-6 h-6 text-blue-600" />
                      </div>
                      <div className="flex-grow">
                        <h3 className="text-lg font-medium text-blue-900 mb-2">
                          OAuth Authentication Account
                        </h3>
                        <p className="text-blue-800 mb-4">
                          You signed in with {providerInfo.name}. Password changes must be managed through your {providerInfo.name} account for security reasons.
                        </p>

                        {providerInfo.url && (
                          <a
                            href={providerInfo.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition-colors duration-200"
                          >
                            <span>Manage {providerInfo.name} Security</span>
                            <ExternalLink className="w-4 h-4 ml-2" />
                          </a>
                        )}

                        <div className="mt-4 p-4 bg-blue-100 rounded-lg">
                          <h4 className="text-sm font-medium text-blue-900 mb-2">Why can't I change my password here?</h4>
                          <ul className="text-sm text-blue-800 space-y-1">
                            <li>• Your account is secured through {providerInfo.name}</li>
                            <li>• This ensures better security and prevents password conflicts</li>
                            <li>• All password changes must be done through {providerInfo.name}'s security settings</li>
                          </ul>
                        </div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <>
                    {/* Success Message */}
                    {success && (
                      <div className="mb-6 p-4 bg-green-50 border border-green-200 rounded-lg">
                        <div className="flex items-center">
                          <CheckCircle className="w-5 h-5 text-green-500 mr-2" />
                          <p className="text-sm text-green-800 font-medium">
                            Password changed successfully!
                          </p>
                        </div>
                      </div>
                    )}

                    {/* Error Message */}
                    {error && (
                      <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg">
                        <div className="flex items-center">
                          <AlertCircle className="w-5 h-5 text-red-500 mr-2" />
                          <p className="text-sm text-red-800">{error}</p>
                        </div>
                      </div>
                    )}

                    <form onSubmit={handlePasswordChange} className="space-y-6">
                      {/* Current Password */}
                      <div>
                        <label htmlFor="currentPassword" className="block text-sm font-medium text-gray-700 mb-2">
                          Current Password
                        </label>
                        <div className="relative">
                          <input
                            id="currentPassword"
                            type={showCurrentPassword ? 'text' : 'password'}
                            value={currentPassword}
                            onChange={(e) => setCurrentPassword(e.target.value)}
                            className="w-full px-4 py-3 border border-gray-300 rounded-xl focus:ring-2 focus:ring-purple-500 focus:border-transparent pr-12"
                            placeholder="Enter your current password"
                            required
                            disabled={isLoading}
                          />
                          <button
                            type="button"
                            onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                            className="absolute inset-y-0 right-0 pr-3 flex items-center"
                            disabled={isLoading}
                          >
                            {showCurrentPassword ? (
                              <EyeOff className="w-5 h-5 text-gray-400" />
                            ) : (
                              <Eye className="w-5 h-5 text-gray-400" />
                            )}
                          </button>
                        </div>
                      </div>

                      {/* New Password */}
                      <div>
                        <label htmlFor="newPassword" className="block text-sm font-medium text-gray-700 mb-2">
                          New Password
                        </label>
                        <div className="relative">
                          <input
                            id="newPassword"
                            type={showNewPassword ? 'text' : 'password'}
                            value={newPassword}
                            onChange={(e) => setNewPassword(e.target.value)}
                            className="w-full px-4 py-3 border border-gray-300 rounded-xl focus:ring-2 focus:ring-purple-500 focus:border-transparent pr-12"
                            placeholder="Enter your new password"
                            required
                            disabled={isLoading}
                          />
                          <button
                            type="button"
                            onClick={() => setShowNewPassword(!showNewPassword)}
                            className="absolute inset-y-0 right-0 pr-3 flex items-center"
                            disabled={isLoading}
                          >
                            {showNewPassword ? (
                              <EyeOff className="w-5 h-5 text-gray-400" />
                            ) : (
                              <Eye className="w-5 h-5 text-gray-400" />
                            )}
                          </button>
                        </div>
                        
                        {/* Password Strength Indicator */}
                        {newPassword && (
                          <div className="mt-2">
                            <div className="flex items-center justify-between mb-1">
                              <span className="text-xs text-gray-600">Password strength:</span>
                              <span className={`text-xs font-medium ${passwordStrength.color}`}>
                                {passwordStrength.text}
                              </span>
                            </div>
                            <div className="w-full bg-gray-200 rounded-full h-2">
                              <div
                                className={`h-2 rounded-full transition-all duration-300 ${
                                  passwordStrength.strength === 1 ? 'bg-red-500 w-1/4' :
                                  passwordStrength.strength === 2 ? 'bg-orange-500 w-2/4' :
                                  passwordStrength.strength === 3 ? 'bg-yellow-500 w-3/4' :
                                  passwordStrength.strength === 4 ? 'bg-green-500 w-full' : 'w-0'
                                }`}
                              />
                            </div>
                          </div>
                        )}
                      </div>

                      {/* Confirm New Password */}
                      <div>
                        <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-700 mb-2">
                          Confirm New Password
                        </label>
                        <div className="relative">
                          <input
                            id="confirmPassword"
                            type={showConfirmPassword ? 'text' : 'password'}
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                            className="w-full px-4 py-3 border border-gray-300 rounded-xl focus:ring-2 focus:ring-purple-500 focus:border-transparent pr-12"
                            placeholder="Confirm your new password"
                            required
                            disabled={isLoading}
                          />
                          <button
                            type="button"
                            onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                            className="absolute inset-y-0 right-0 pr-3 flex items-center"
                            disabled={isLoading}
                          >
                            {showConfirmPassword ? (
                              <EyeOff className="w-5 h-5 text-gray-400" />
                            ) : (
                              <Eye className="w-5 h-5 text-gray-400" />
                            )}
                          </button>
                        </div>
                        
                        {/* Password Match Indicator */}
                        {confirmPassword && (
                          <div className="mt-2">
                            {newPassword === confirmPassword ? (
                              <div className="flex items-center text-green-600">
                                <CheckCircle className="w-4 h-4 mr-1" />
                                <span className="text-xs">Passwords match</span>
                              </div>
                            ) : (
                              <div className="flex items-center text-red-600">
                                <AlertCircle className="w-4 h-4 mr-1" />
                                <span className="text-xs">Passwords don't match</span>
                              </div>
                            )}
                          </div>
                        )}
                      </div>

                      {/* Submit Button */}
                      <button
                        type="submit"
                        disabled={isLoading || newPassword.length < 8 || newPassword !== confirmPassword || !currentPassword}
                        className="w-full bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed text-white font-semibold py-3 px-6 rounded-xl transition-all duration-200 transform hover:scale-[1.02] shadow-lg disabled:hover:scale-100"
                      >
                        {isLoading ? (
                          <div className="flex items-center justify-center">
                            <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white mr-2"></div>
                            Changing Password...
                          </div>
                        ) : (
                          'Change Password'
                        )}
                      </button>
                    </form>

                    {/* Password Requirements */}
                    <div className="mt-6 p-4 bg-gray-50 rounded-lg">
                      <h4 className="text-sm font-medium text-gray-700 mb-2">Password Requirements:</h4>
                      <ul className="text-xs text-gray-600 space-y-1">
                        <li className="flex items-center">
                          <div className={`w-2 h-2 rounded-full mr-2 ${newPassword.length >= 8 ? 'bg-green-500' : 'bg-gray-300'}`} />
                          At least 8 characters long
                        </li>
                        <li className="flex items-center">
                          <div className={`w-2 h-2 rounded-full mr-2 ${/[a-z]/.test(newPassword) && /[A-Z]/.test(newPassword) ? 'bg-green-500' : 'bg-gray-300'}`} />
                          Mix of uppercase and lowercase letters
                        </li>
                        <li className="flex items-center">
                          <div className={`w-2 h-2 rounded-full mr-2 ${/[0-9]/.test(newPassword) ? 'bg-green-500' : 'bg-gray-300'}`} />
                          At least one number
                        </li>
                        <li className="flex items-center">
                          <div className={`w-2 h-2 rounded-full mr-2 ${/[^A-Za-z0-9]/.test(newPassword) ? 'bg-green-500' : 'bg-gray-300'}`} />
                          At least one special character
                        </li>
                      </ul>
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function SettingsPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen bg-gradient-to-br from-purple-50 to-blue-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-3xl shadow-2xl border border-purple-200/50 p-8 w-full max-w-md">
          <div className="text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-600 mx-auto mb-4"></div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">Loading Settings...</h2>
            <p className="text-gray-600">Please wait while we load your account settings...</p>
          </div>
        </div>
      </div>
    }>
      <SettingsContent />
    </Suspense>
  );
}