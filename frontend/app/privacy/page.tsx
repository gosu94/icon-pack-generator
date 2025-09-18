'use client';

import React from 'react';
import Navigation from '../../components/Navigation';
import Footer from '../../components/Footer';
import Link from 'next/link';

export default function PrivacyPolicy() {
  return (
    <>
      <Navigation />
      <div className="min-h-screen bg-gradient-to-br from-slate-50 to-blue-50">
        <div className="max-w-4xl mx-auto px-6 py-12">
          <div className="bg-white rounded-2xl shadow-xl p-8 lg:p-12">
            <h1 className="text-4xl font-bold text-gray-900 mb-8">Privacy Policy</h1>
            
            <div className="prose prose-lg max-w-none text-gray-700">
              <p className="text-sm text-gray-500 mb-8">
                <strong>Effective Date:</strong> {new Date().toLocaleDateString()}
              </p>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">1. Information We Collect</h2>
                <p className="mb-4">
                  We collect minimal information necessary to provide our icon generation service:
                </p>
                <ul className="list-disc pl-6 mb-4">
                  <li><strong>Email Address:</strong> Required for account creation and communication</li>
                  <li><strong>Password:</strong> If you create an account with email/password authentication (encrypted using BCrypt)</li>
                  <li><strong>Google Account Information:</strong> If you sign in with Google, we receive your email address</li>
                  <li><strong>Coin Balance:</strong> The number of coins in your account for purchasing icon generation services</li>
                  <li><strong>Payment Information:</strong> Processed securely through Stripe (we do not store payment details)</li>
                </ul>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">2. How We Use Your Information</h2>
                <p className="mb-4">We use your information to:</p>
                <ul className="list-disc pl-6 mb-4">
                  <li>Provide and maintain our icon generation service</li>
                  <li>Process payments and manage your coin balance</li>
                  <li>Send important service-related communications</li>
                  <li>Respond to your inquiries and provide customer support</li>
                  <li>Improve our service and user experience</li>
                </ul>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">3. Data Security</h2>
                <p className="mb-4">
                  We implement appropriate security measures to protect your personal information:
                </p>
                <ul className="list-disc pl-6 mb-4">
                  <li>Passwords are encrypted using BCrypt hashing</li>
                  <li>Payment processing is handled securely by Stripe</li>
                  <li>Data transmission is encrypted using industry-standard protocols</li>
                  <li>Access to personal data is restricted to authorized personnel only</li>
                </ul>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">4. Third-Party Services</h2>
                <p className="mb-4">We use the following third-party services:</p>
                <ul className="list-disc pl-6 mb-4">
                  <li><strong>Google OAuth:</strong> For Google Sign-In authentication</li>
                  <li><strong>Stripe:</strong> For secure payment processing</li>
                </ul>
                <p className="mb-4">
                  These services have their own privacy policies and handle your data according to their terms.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">5. Data Sharing</h2>
                <p className="mb-4">
                  We do not sell, trade, or share your personal information with third parties, except:
                </p>
                <ul className="list-disc pl-6 mb-4">
                  <li>With your explicit consent</li>
                  <li>To comply with legal obligations</li>
                  <li>To protect our rights and prevent fraud</li>
                  <li>With service providers necessary for business operations (under strict confidentiality)</li>
                </ul>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">6. Data Retention</h2>
                <p className="mb-4">
                  We retain your personal information only as long as necessary to provide our services and comply with legal obligations. You may request account deletion at any time.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">7. Your Rights</h2>
                <p className="mb-4">You have the right to:</p>
                <ul className="list-disc pl-6 mb-4">
                  <li>Access your personal data</li>
                  <li>Correct inaccurate information</li>
                  <li>Request deletion of your account and data</li>
                  <li>Object to data processing</li>
                  <li>Data portability</li>
                </ul>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">8. Cookies</h2>
                <p className="mb-4">
                  We use essential cookies to maintain your login session and provide core functionality. We do not use tracking cookies or analytics cookies without your consent.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">9. Changes to This Policy</h2>
                <p className="mb-4">
                  We may update this privacy policy from time to time. We will notify you of any material changes by posting the new policy on this page.
                </p>
              </section>

              <section className="mb-8">
                <h2 className="text-2xl font-semibold text-gray-900 mb-4">10. Contact Information</h2>
                <div className="bg-gray-50 p-6 rounded-lg">
                  <p className="mb-2"><strong>Data Controller:</strong> Tomasz Pilarczyk</p>
                  <p className="mb-2"><strong>Address:</strong> Żwirki i Wigury 16, 66-620 Gubin, Poland</p>
                  <p className="mb-2"><strong>Tax ID:</strong> PL9261689360</p>
                  <p className="mb-2"><strong>Email:</strong> support@iconpackgen.com</p>
                </div>
                <p className="mt-4">
                  If you have questions about this privacy policy or how we handle your data, please contact us using the information above.
                </p>
              </section>
            </div>

            <div className="mt-12 pt-8 border-t border-gray-200 text-center">
              <Link 
                href="/" 
                className="inline-flex items-center px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors duration-200 font-medium"
              >
                Back to Home
              </Link>
            </div>
          </div>
        </div>
      </div>
      <Footer />
    </>
  );
}
