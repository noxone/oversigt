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
import { DashboardInfo } from './dashboardInfo';


export interface EventSourceInstanceInfo {
    id?: string;
    name?: string;
    enabled?: boolean;
    running?: boolean;
    hasError?: boolean;
    usedBy: Array<DashboardInfo>;
    service?: boolean;
}
