import { Place } from './PlaceProvider';

export const PlaceCard = (props: Place) => {
  return (
    <div className="w-auto bg-white rounded-xl shadow-md overflow-hidden md:max-w-2xl">
      <div>
        <div className="p-8">
          <span className="mx-auto block text-lg font-medium text-black">
            {props.title}
          </span>
          <p className="mt-2 text-gray-500">{props.description}</p>
        </div>
      </div>
    </div>
  );
}