import { defineStore } from 'pinia';
import { fetchProfile, login, logout } from '../services/user';
import { clearAuthenticationStorage, TOKEN_KEY, TOKEN_NAME_KEY, USER_ID_KEY } from '../services/authStorage';

export const useUserStore = defineStore('user', {
  state: () => ({
    userId: Number(localStorage.getItem(USER_ID_KEY) || 0),
    token: localStorage.getItem(TOKEN_KEY) || '',
    tokenName: localStorage.getItem(TOKEN_NAME_KEY) || 'Authorization',
    profile: null
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token && state.userId),
    isVip: (state) => Boolean(state.profile?.vipActive)
  },
  actions: {
    async signIn(payload) {
      const data = await login(typeof payload === 'string' ? { mobile: payload } : payload);
      this.userId = data.id;
      this.token = data.tokenValue || '';
      this.tokenName = data.tokenName || 'Authorization';
      this.profile = data;
      localStorage.setItem(USER_ID_KEY, String(data.id));
      localStorage.setItem(TOKEN_KEY, this.token);
      localStorage.setItem(TOKEN_NAME_KEY, this.tokenName);
      return data;
    },
    async loadProfile() {
      if (!this.isAuthenticated) {
        this.profile = null;
        return null;
      }
      this.profile = await fetchProfile();
      return this.profile;
    },
    async signOut() {
      if (this.token) {
        try {
          await logout();
        } catch {
          // Local logout should still clear an expired or invalid session.
        }
      }
      this.clearSession();
    },
    clearSession() {
      this.userId = 0;
      this.token = '';
      this.tokenName = 'Authorization';
      this.profile = null;
      clearAuthenticationStorage();
    }
  }
});
