import { useEffect, useState } from 'react';
import { BehaviorSubject, debounceTime, map, timer, catchError, combineLatest, takeUntil, share, filter, distinctUntilChanged, mergeMap, from, switchMap, first, startWith, publish, merge, Observable, of, distinct, toArray } from 'rxjs';
import { fromFetch } from 'rxjs/fetch';
import { GRAPHHOPPER_API_KEY } from '../config';
import { useObservable, useRequestObservable, UseRequestObservableResult } from '../utils';

interface PlaceProviderProps {
  query: string;
  children: (places: Place[] | null, isLoading: boolean, error: Error | null) => JSX.Element;
}

export interface Place {
  id: string;
  title: string;
  lat: number;
  lng: number;
}

type Result = UseRequestObservableResult<Place[]>;

const placesSubject = new BehaviorSubject<string>('');
const placesObservable = placesSubject.pipe(
  debounceTime(200),
  distinctUntilChanged(),
  switchMap(query => {
      if (query.length > 1) {
        return fromFetch(
          `https://graphhopper.com/api/1/geocode?q=${query}&locale=ru&debug=true&key=${GRAPHHOPPER_API_KEY}&limit=100`
        ).pipe(
          switchMap(response => {
            if (response.ok) {
              return response.json();
            }
            throw new Error(response.statusText);
          }),
          switchMap(responseJson => from(responseJson.hits).pipe(
              map(({ country, city, name, point, osm_id }: any) => 
                ({ 
                  title: [country, city, name].filter(p => !!p).join(', '),
                  id: osm_id.toString(),
                  ...point
                } as Place)
              ),
              distinct(place => place.title),
              toArray(),
              map(places => ({ status: 'ok', data: places } as Result))
            )
          ),
          startWith({ status: 'loading' } as Result),
          catchError(async error => ({
            status: 'error',
            error
          } as Result)),
        );
      } else {
        return of({ status: 'ok', data: [] as Place[] } as Result);
      }
    }
  )
);


export const PlaceProvider = ({ query, children }: PlaceProviderProps) => {
  const [place, isLoading, error] = useRequestObservable(query, placesSubject, placesObservable);

  return children(
    place,
    isLoading,
    error
  );
}