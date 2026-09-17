export async function startGoogleSignIn(client, origin) {
  const url = new URL(origin);
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost','127.0.0.1'].includes(url.hostname))) {
    throw new Error('Sign-in requires a secure site.');
  }
  const {error} = await client.auth.signInWithOAuth({
    provider: 'google',
    options: {redirectTo: url.origin + '/?account=1', queryParams: {prompt: 'select_account'}},
  });
  if (error) throw new Error('Google sign-in could not start. Please try again shortly.');
}
