import { useEffect, useState } from 'react';
import { Observable, Subject } from 'rxjs';

export const useObservable = <T>(observable: Observable<T>, setter: (value: T) => void) => {
  useEffect(() => {
    const subscription = observable.subscribe(result => setter(result));
    return () => subscription.unsubscribe();
  }, []);
};

export interface UseRequestObservableResult<T> {
  status: 'ok' | 'loading' | 'error';
  error?: Error;
  data?: T;
}

export const useRequestObservable = <T, P>(input: P, subject: Subject<P>, observable: Observable<UseRequestObservableResult<T>>)
  : [T | null, boolean, Error | null] => {
  const [data, setData] = useState<T | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    subject.next(input);
  }, [input]);

  useObservable(observable, ({ data, status, error }: UseRequestObservableResult<T>) => {
    if (status === 'ok' && data) {
      setData(data);
      setError(null);
      setIsLoading(false);
    } else if (status === 'loading') {
      setIsLoading(true);
      setError(null);
    } else if (status === 'error' && error) {
      setError(error);
      setIsLoading(false);
    }
  });

  return [data, isLoading, error];
}