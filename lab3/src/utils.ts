import { useEffect } from 'react';
import { Observable } from 'rxjs';

export const useObservable = <T>(observable: Observable<T>, setter: (value: T) => void) => {
  useEffect(() => {
    const subscription = observable.subscribe(result => setter(result));
    return () => subscription.unsubscribe();
  }, []);
};