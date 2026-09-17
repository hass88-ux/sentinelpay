import {expect,it,vi} from 'vitest';
import {startGoogleSignIn} from './google-auth';
it('returns to the account page and requests no extra Google permissions',async()=>{
  const signInWithOAuth=vi.fn().mockResolvedValue({error:null});
  await startGoogleSignIn({auth:{signInWithOAuth}},'https://sentinelpay.example');
  expect(signInWithOAuth).toHaveBeenCalledWith({provider:'google',options:{redirectTo:'https://sentinelpay.example/?account=1',queryParams:{prompt:'select_account'}}});
});
it('reports a failed provider setup without displaying provider details',async()=>{
  await expect(startGoogleSignIn({auth:{signInWithOAuth:vi.fn().mockResolvedValue({error:{message:'internal'}})}},'https://sentinelpay.example')).rejects.toThrow('could not start');
});
it('rejects an insecure public redirect',async()=>{
  const signInWithOAuth=vi.fn();
  await expect(startGoogleSignIn({auth:{signInWithOAuth}},'http://public.example')).rejects.toThrow('secure');
  expect(signInWithOAuth).not.toHaveBeenCalled();
});
