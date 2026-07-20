export function shouldCheckVipStatus(authenticated) {
  return authenticated === true;
}

export function canRequestVipContent(authenticated, status) {
  return authenticated === true && status?.active === true;
}
