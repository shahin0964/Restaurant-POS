import React, { useState, useEffect } from "react";
import { initializeApp, getApp, getApps } from "firebase/app";
import { getAuth, createUserWithEmailAndPassword, signOut } from "firebase/auth";
import { getDatabase, ref, set, onValue, remove, update } from "firebase/database";
import { useAuth } from "./AuthContext"; // Standard react hook to access current active admin auth details

// Firebase configuration placeholder - should match your primary Firebase app settings
const firebaseConfig = {
  apiKey: "YOUR_API_KEY_HERE",
  authDomain: "restaurant-pos-99d57.firebaseapp.com",
  databaseURL: "https://restaurant-pos-99d57-default-rtdb.asia-southeast1.firebasedatabase.app/",
  projectId: "restaurant-pos-99d57",
  storageBucket: "restaurant-pos-99d57.appspot.com",
  messagingSenderId: "YOUR_SENDER_ID",
  appId: "YOUR_APP_ID"
};

/**
 * StaffManagement component for administrative user orchestration.
 * Uses a secondary Firebase App instance so the current administrator session remains logged in.
 */
export const StaffManagement = () => {
  const { currentUser: adminUser } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  // Form State
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("staff");

  // Fetch staff list from Realtime Database
  useEffect(() => {
    const db = getDatabase();
    const usersRef = ref(db, "users");

    const unsubscribe = onValue(usersRef, (snapshot) => {
      const data = snapshot.val();
      if (data) {
        const userList = Object.keys(data).map((key) => ({
          uid: key,
          ...data[key],
        }));
        setUsers(userList);
      } else {
        setUsers([]);
      }
      setLoading(false);
    }, (err) => {
      setError("Failed to fetch staff list: " + err.message);
      setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  // Handle staff creation
  const handleCreateStaff = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");

    if (!name.trim() || !email.trim() || !password.trim()) {
      setError("Please fill in all required fields.");
      return;
    }

    if (password.length < 6) {
      setError("Password must be at least 6 characters long.");
      return;
    }

    try {
      // 1. Initialize secondary Firebase app instance to preserve admin session
      const secondaryAppName = `StaffCreator_${Date.now()}`;
      const secondaryApp = initializeApp(firebaseConfig, secondaryAppName);
      const secondaryAuth = getAuth(secondaryApp);

      // 2. Register secondary user
      const userCredential = await createUserWithEmailAndPassword(secondaryAuth, email, password);
      const newUid = userCredential.user.uid;

      // 3. Write staff profile parameters under users/{newUid} node
      const db = getDatabase();
      const userRef = ref(db, `users/${newUid}`);
      
      const subAccountPayload = {
        uid: newUid,
        name: name.trim(),
        email: email.trim(),
        role: role.toLowerCase().trim(),
        createdBy: adminUser?.uid || "unknown_admin",
        createdAt: Date.now()
      };

      await set(userRef, subAccountPayload);

      // 4. Safely clean up secondary Firebase App instance
      await signOut(secondaryAuth);
      
      setSuccess(`Sub-account for "${name}" created successfully!`);
      
      // Reset fields
      setName("");
      setEmail("");
      setPassword("");
      setRole("staff");
    } catch (err) {
      setError(err.message || "An error occurred during account registration.");
    }
  };

  // Handle staff deletion
  const handleDeleteStaff = async (targetUid, targetName) => {
    if (targetUid === adminUser?.uid) {
      setError("You cannot delete your own logged-in administrator account.");
      return;
    }

    const confirmDelete = window.confirm(`Are you sure you want to delete staff account: ${targetName}?`);
    if (!confirmDelete) return;

    setError("");
    setSuccess("");

    try {
      const db = getDatabase();
      const userRef = ref(db, `users/${targetUid}`);
      await remove(userRef);
      setSuccess(`Staff account "${targetName}" removed successfully.`);
    } catch (err) {
      setError("Failed to delete account: " + err.message);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-8">
      <div className="max-w-6xl mx-auto space-y-8">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-amber-500">Staff & Sub-Account Management</h1>
          <p className="text-slate-400 mt-2">Provision new staff profiles, assign roles, and administer system access control.</p>
        </div>

        {error && <div className="p-4 bg-red-950/50 border border-red-800 text-red-200 rounded-lg">{error}</div>}
        {success && <div className="p-4 bg-emerald-950/50 border border-emerald-800 text-emerald-200 rounded-lg">{success}</div>}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Creation Form */}
          <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-lg space-y-6">
            <h2 className="text-xl font-bold text-slate-100 border-b border-slate-800 pb-3">Create Sub-Account</h2>
            
            <form onSubmit={handleCreateStaff} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Full Name</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Michael Scott"
                  className="w-full px-4 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100 placeholder-slate-500 focus:outline-none focus:border-amber-500"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Email Address</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="e.g. manager@restaurant.com"
                  className="w-full px-4 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100 placeholder-slate-500 focus:outline-none focus:border-amber-500"
                  required
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
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">Assigned Role</label>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value)}
                  className="w-full px-4 py-2 bg-slate-950 border border-slate-800 rounded-lg text-slate-100 focus:outline-none focus:border-amber-500"
                >
                  <option value="admin">Admin (Operational Administrative Access)</option>
                  <option value="manager">Manager (Menu, Reports, Inventory)</option>
                  <option value="cashier">Cashier (Billing, Order taking)</option>
                  <option value="staff">Staff/Waiter (Order taking, Kitchen display)</option>
                  <option value="user">User (View-Only Menu)</option>
                </select>
              </div>

              <button
                type="submit"
                className="w-full py-2.5 bg-amber-500 text-slate-950 font-bold rounded-lg hover:bg-amber-400 transition-colors"
              >
                CREATE STAFF ACCOUNT
              </button>
            </form>
          </div>

          {/* Active Accounts List */}
          <div className="lg:col-span-2 bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-lg space-y-4">
            <h2 className="text-xl font-bold text-slate-100 border-b border-slate-800 pb-3">Active System Staff</h2>

            {loading ? (
              <div className="flex items-center justify-center py-12">
                <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-amber-500"></div>
              </div>
            ) : users.length === 0 ? (
              <p className="text-slate-500 text-center py-12">No active staff accounts mapped.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="border-b border-slate-800 text-slate-400 text-xs uppercase tracking-wider">
                      <th className="py-3 px-4">Name</th>
                      <th className="py-3 px-4">Email</th>
                      <th className="py-3 px-4">Role</th>
                      <th className="py-3 px-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800">
                    {users.map((staff) => (
                      <tr key={staff.uid} className="hover:bg-slate-950/40 text-sm">
                        <td className="py-3.5 px-4 font-semibold text-slate-100">{staff.name}</td>
                        <td className="py-3.5 px-4 text-slate-400">{staff.email}</td>
                        <td className="py-3.5 px-4">
                          <span className={`px-2 py-1 rounded text-xs font-bold ${
                            staff.role === "administrator"
                              ? "bg-amber-500/10 text-amber-500 border border-amber-500/25"
                              : staff.role === "admin"
                              ? "bg-amber-500/10 text-amber-500 border border-amber-500/25"
                              : staff.role === "manager"
                              ? "bg-blue-500/10 text-blue-400 border border-blue-500/25"
                              : "bg-slate-800 text-slate-300 border border-slate-700/50"
                          }`}>
                            {staff.role}
                          </span>
                        </td>
                        <td className="py-3.5 px-4 text-right">
                          <button
                            onClick={() => handleDeleteStaff(staff.uid, staff.name)}
                            disabled={staff.role === "administrator"}
                            className="text-xs px-2.5 py-1 text-red-400 hover:text-red-300 bg-red-950/20 hover:bg-red-950/50 rounded border border-red-900/30 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                          >
                            DELETE
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
