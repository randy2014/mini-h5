import { defineStore } from 'pinia';
import { fetchProfile, login } from '../services/user';

export const useUserStore = defineStore('user', {
  state: () => ({
    userId: Number(localStorage.getItem('mini_novel_user_id') || 1),
    profile: null
  }),
  getters: {
    isVip: (state) => Boolean(state.profile?.vipActive)
  },
  actions: {
    async signIn(mobile) {
      const data = await login({ mobile });
      this.userId = data.id;
      localStorage.setItem('mini_novel_user_id', String(data.id));
      await this.loadProfile();
    },
    async loadProfile() {
      this.profile = await fetchProfile();
    },
    switchDemoUser(userId) {
      this.userId = userId;
      localStorage.setItem('mini_novel_user_id', String(userId));
      return this.loadProfile();
    }
  }
});
