import { useEffect, useState } from 'react';
import { BehaviorSubject, startWith, switchMap, filter, of, mergeMap, take, tap, map, mergeAll, toArray, catchError, distinctUntilKeyChanged, MonoTypeOperatorFunction, Observable, concatMap, asyncScheduler, distinct, distinctUntilChanged } from 'rxjs';
import { fromFetch } from 'rxjs/fetch';
import { OPENTRIPMAP_API_KEY } from '../config';
import { useRequestObservable, UseRequestObservableResult } from '../utils';
import { Place } from './PlaceProvider';

interface GoodSpot {
  id: string;
  name: string;
  imageSrc?: string;
  description?: string;
}

interface PlaceDataProviderProps {
  place: Place;
  children: (placeData: GoodSpot[] | null, isLoading: boolean, error: Error | null) => JSX.Element;
}

type Result = UseRequestObservableResult<GoodSpot[]>;

const placeDataSubject = new BehaviorSubject<Place | null>(null);
const placeDataObservable = placeDataSubject.pipe(
  filter(place => !!place),
  switchMap(place => {
    return fromFetch(
      `https://api.opentripmap.com/0.1/ru/places/radius?radius=10000&rate=3&lon=${place!.lng}&lat=${place!.lat}&apikey=${OPENTRIPMAP_API_KEY}`
    ).pipe(
      mergeMap(response => {
        if (response.ok) {
          return response.json()
        }
        throw new Error(response.statusText);
      }),
      mergeMap(responseJson => {
        return of(responseJson.features).pipe(
          mergeAll(),
          take(4),
          mergeMap((feature: any) => {
            return fromFetch(
              `https://api.opentripmap.com/0.1/ru/places/xid/${feature.properties.xid}?apikey=${OPENTRIPMAP_API_KEY}`
            ).pipe(
              switchMap(response => {
                if (response.ok) {
                  return response.json();
                }
                throw new Error(response.statusText);
              }),
              map(responseJson => ({
                  id: responseJson.xid,
                  name: responseJson.name,
                  imageSrc: responseJson.preview?.source,
                  description: responseJson.wikipedia_extracts?.text
                } as GoodSpot)
              ),
              filter(spot => !!spot.name)
            )
          }),
          distinct(spot => spot.name),
          toArray()
        )
      }),
      map(data => ({ status: 'ok', data } as Result)),
      catchError(async error => ({
        status: 'error',
        error
      } as Result)),
      startWith({ status: 'loading' } as Result)
    )
  })
)

export const PlaceDataProvider = ({ place, children }: PlaceDataProviderProps) => {
  const [placeData, isLoading, error] = useRequestObservable(place, placeDataSubject, placeDataObservable);

  return children(
    placeData,
    isLoading,
    error
  );
}