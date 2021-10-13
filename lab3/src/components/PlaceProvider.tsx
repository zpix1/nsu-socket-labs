import { useEffect, useState } from 'react';
import { BehaviorSubject, debounceTime, map, timer, mapTo, combineLatest, takeUntil, share, filter, distinctUntilChanged, mergeMap, from, switchMap, first, startWith, publish, merge, Observable, of } from 'rxjs';
import { fromFetch } from 'rxjs/fetch';
import { GRAPHHOPPER_API_KEY } from '../config';
import { useObservable } from '../utils';

interface PlaceProviderProps {
  query: string;
  children: (places: Place[], isLoading: boolean, error: Error | null) => JSX.Element;
}

interface Place {
  title?: string;
  description?: string;
  weather?: string;
}

interface Result {
  status: 'ok' | 'error' | 'loading';
  places: Place[];
  error?: Error;
}

const placesSubject = new BehaviorSubject<string>('');
const placesObservable = placesSubject.pipe(
  debounceTime(200),
  distinctUntilChanged(),
  switchMap(query => {
      if (query.length > 1) {
        return fromFetch(
          `https://graphhopper.com/api/1/geocode?q=${query}&locale=ru&debug=true&key=${GRAPHHOPPER_API_KEY}`
        ).pipe(
          switchMap(async response => {
            if (response.ok) {
              const content = (await response.json()).hits;
              const places = content.map(({country, city, name}: any) => 
                ({ title: [country, city, name].filter(p => !!p).join(', ') } as Place)
              );
              return { status: 'ok', places: places } as Result;
            }
            return { status: 'error', error: new Error(`Error: ${response.statusText}`) } as Result;
          }),
          startWith({ status: 'loading' } as Result)
        );
      } else {
        return of({ status: 'ok', places: [] } as Result);
      }
    }
  )
);


export const PlaceProvider = ({ query, children }: PlaceProviderProps) => {
  const [places, setPlaces] = useState<Place[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    placesSubject.next(query);
  }, [query]);

  useObservable(placesObservable, ({ places, status, error }: Result) => {
    console.log(status);
    if (status === 'ok') {
      setPlaces(places);
      setIsLoading(false);
    } else if (status === 'loading') {
      console.log('loading');
      setIsLoading(true);
    } else if (status === 'error' && error) {
      setError(error);
      setIsLoading(false);
    }
  });

  return children(places, isLoading, error);
}