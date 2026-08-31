import { GoogleAuthProvider, createUserWithEmailAndPassword, signInWithEmailAndPassword, signInWithPopup, signOut } from 'firebase/auth';
import { firebaseAuth } from './firebase';

function requireAuth() {
  if (!firebaseAuth) throw new Error('Firebase is not configured');
  return firebaseAuth;
}

export const FirebaseAuthService = {
  signUpWithEmail(email: string, password: string) {
    return createUserWithEmailAndPassword(requireAuth(), email, password);
  },
  signInWithEmail(email: string, password: string) {
    return signInWithEmailAndPassword(requireAuth(), email, password);
  },
  signInWithGoogle() {
    return signInWithPopup(requireAuth(), new GoogleAuthProvider());
  },
  signOut() {
    return signOut(requireAuth());
  },
};
