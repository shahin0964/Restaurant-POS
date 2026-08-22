import React, { useState } from "react";
import { getAuth, createUserWithEmailAndPassword, updateProfile } from "firebase/auth";
import { getDatabase, ref, set } from "firebase/database";

/**
 * Public Self-Registration Signup Page for the Primary Administrator/Owner.
 * Any user registering via this page automatically gets the "admin" role.
 */
export const Signup = () => {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const handleSignup = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    setLoading(true);

    if (!name.trim() || !email.trim() || !password.trim()) {
      setError("Please fill in all fields.");
      setLoading(false);
      return;
    }

    if (password.length < 6) {
      setError("Password must be at least 6 characters.");
      setLoading(false);
      return;
    }

    try {
      const auth = getAuth();
      // 1. Create authentication profile in Firebase
      const userCredential = await createUserWithEmailAndPassword(auth, email.trim(), password);
      const user = userCredential.user;

      // 2. Set user display name
      await updateProfile(user, { displayName: name.trim() });

      // 3. Write user profile with admin role default directly to Realtime Database under users/{userId}
      const db = getDatabase();
      const adminProfileRef = ref(db, `users/${user.uid}`);
      
      const adminPayload = {
        uid: user.uid,
        name: name.trim(),
        email: email.trim(),
        role: "Administrator", // Automatically assigned role: "Administrator" by default!
        createdAt: Date.now()
      };

      await set(adminProfileRef, adminPayload);

      setSuccess("Administrator account registered successfully! Redirecting...");
      setTimeout(() => {
        window.location.href = "/dashboard";
      }, 1500);
    } catch (err) {
      setError(err.message || "An error occurred during administrator signup.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-6 text-slate-100">
      <div className="w-full max-w-md bg-slate-900 border border-slate-800 p-8 rounded-2xl shadow-xl space-y-6">
        <div className="text-center">
          <h1 className="text-3xl font-bold tracking-tight text-amber-500">POS Register</h1>
          <p className="text-slate-400 mt-2">Create your primary Restaurant Owner / Admin account</p>
        </div>

        {error && <div className="p-4 bg-red-950/50 border border-red-800 text-red-200 rounded-lg text-sm">{error}</div>}
        {success && <div className="p-4 bg-emerald-950/50 border border-emerald-800 text-emerald-200 rounded-lg text-sm">{success}</div>}

        <form onSubmit={handleSignup} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">Owner / Restaurant Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Luigi's Pizza"
              className="w-full px-4 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100 placeholder-slate-500 focus:outline-none focus:border-amber-500"
              required
              disabled={loading}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">Email Address</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@restaurant.com"
              className="w-full px-4 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100 placeholder-slate-500 focus:outline-none focus:border-amber-500"
              required
              disabled={loading}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Min. 6 characters"
              className="w-full px-4 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100 placeholder-slate-500 focus:outline-none focus:border-amber-500"
              required
              disabled={loading}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 bg-amber-500 text-slate-950 font-bold rounded-lg hover:bg-amber-400 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? "REGISTERING..." : "REGISTER PRIMARY OWNER"}
          </button>
        </form>

        <p className="text-xs text-center text-slate-500">
          Note: This self-signup route is strictly reserved for the primary store owner. Sub-accounts for cashiers and staff must be created inside the admin dashboard under Staff Users.
        </p>
      </div>
    </div>
  );
};
