export const USER_ID_KEY = 'mini_novel_user_id';
export const TOKEN_KEY = 'mini_novel_auth_token';
export const TOKEN_NAME_KEY = 'mini_novel_auth_token_name';

export function clearAuthenticationStorage(storage = localStorage) {
  storage.removeItem(USER_ID_KEY);
  storage.removeItem(TOKEN_KEY);
  storage.removeItem(TOKEN_NAME_KEY);
}
