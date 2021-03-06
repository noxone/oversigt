/**
 * Oversigt API
 * This API provides access to all public operations of Oversigt.
 *
 * OpenAPI spec version: 1.0
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


export interface WidgetDetails {
    id: number;
    eventSourceInstanceId: string;
    type: string;
    title: string;
    name: string;
    view: string;
    enabled: boolean;
    posX: number;
    posY: number;
    sizeX: number;
    sizeY: number;
    backgroundColor: string;
    style: string;
    data: { [key: string]: string; };
}
