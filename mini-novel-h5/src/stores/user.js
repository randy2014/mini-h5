import { defineStore } from 'pinia';
import { fetchProfile, login, logout } from '../services/user';

const USER_ID_KEY = 'mini_novel_user_id';
const TOKEN_KEY = 'mini_novel_auth_token';
const TOKEN_NAME_KEY = 'mini_novel_auth_token_name';

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
    async signIn(mobile) {
      const data = await login({ mobile });
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
      localStorage.removeItem(USER_ID_KEY);
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(TOKEN_NAME_KEY);
    }
  }
});
