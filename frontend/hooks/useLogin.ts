import { useState } from 'react';
import { useAuth } from '../context/AuthContext';

export type LoginStep = 'method' | 'email' | 'password' | 'loading' | 'success' | 'email-sent';

export function useLogin() {
  const { checkAuthenticationStatus } = useAuth();
  
  // State management
  const [loginStep, setLoginStep] = useState<LoginStep>('method');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [authError, setAuthError] = useState('');
  const [isEmailLoading, setIsEmailLoading] = useState(false);
  const [isPasswordLoading, setIsPasswordLoading] = useState(false);
  const [emailExists, setEmailExists] = useState(false);

  const resetState = () => {
    setLoginStep('method');
    setEmail('');
    setPassword('');
    setShowPassword(false);
    setAuthError('');
    setEmailExists(false);
    setIsEmailLoading(false);
    setIsPasswordLoading(false);
  };

  const handleGoogleLogin = (redirectUrl?: string) => {
    if (redirectUrl) {
      sessionStorage.setItem('loginRedirect', redirectUrl);
    }
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
          setLoginStep('password');
        } else {
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

  const handlePasswordSubmit = async (e: React.FormEvent, onSuccess?: (redirectUrl?: string) => void) => {
    e.preventDefault();
    setIsPasswordLoading(true);
    setAuthError('');

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, password }),
        credentials: 'include',
      });

      const data = await response.json();

      if (response.ok && data.success) {
        setLoginStep('success');
        await checkAuthenticationStatus();
        
        setTimeout(() => {
          if (onSuccess) {
            onSuccess('/dashboard');
          }
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

  return {
    // State
    loginStep,
    email,
    password,
    showPassword,
    authError,
    isEmailLoading,
    isPasswordLoading,
    emailExists,
    
    // Setters
    setEmail,
    setPassword,
    setShowPassword,
    
    // Actions
    resetState,
    handleGoogleLogin,
    handleEmailContinue,
    handleBackToMethod,
    handleEmailSubmit,
    handlePasswordSubmit,
    handleForgotPassword,
  };
}
