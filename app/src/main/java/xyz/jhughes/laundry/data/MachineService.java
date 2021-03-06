package xyz.jhughes.laundry.data;


import xyz.jhughes.laundry.laundryparser.Machine;
import xyz.jhughes.laundry.laundryparser.MachineList;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import xyz.jhughes.laundry.laundryparser.LocationResponse;

/**
 * Retrofit interface for the Machine API.
 */
public interface MachineService {
    @GET("/v2/location/{location}")
    Call<List<Machine>> getMachineStatus(
            @Path("location") String location
    );

    @GET("/v2-debug/location/{location}")
    Call<List<Machine>> getMachineStatus_DEBUG(
            @Path("location") String location
    );

    @GET("/v2/location/all")
    Call<Map<String,MachineList>> getAllMachines();

    @GET("/v2-debug/location/all")
    Call<Map<String,MachineList>> getAllMachines_DEBUG();

    @GET("/v2/locations")
    Call<List<LocationResponse>> getLocations();

    @GET("/v2-debug/locations")
    Call<List<LocationResponse>> getLocations_DEBUG();
}
