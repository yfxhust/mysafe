/*
 * Copyright (c) 2017, Serkan OZAL, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tr.com.serkanozal.mysafe.config;

public class ReallocationPointConfig {

    public static final int DEFAULT_OLD_ADDRESS_PARAMETER_ORDER = 1;
    public static final int DEFAULT_NEW_SIZE_PARAMETER_ORDER = 2;
    
    public final int oldAddressParameterOrder;
    public final int newSizeParameterOrder;
    
    public ReallocationPointConfig() {
        this.oldAddressParameterOrder = DEFAULT_OLD_ADDRESS_PARAMETER_ORDER;
        this.newSizeParameterOrder = DEFAULT_NEW_SIZE_PARAMETER_ORDER;
    }
    
    public ReallocationPointConfig(int oldAddressParameterOrder) {
        this.oldAddressParameterOrder = oldAddressParameterOrder;
        this.newSizeParameterOrder = DEFAULT_NEW_SIZE_PARAMETER_ORDER;
    }
    
    public ReallocationPointConfig(int oldAddressParameterOrder, int newSizeParameterOrder) {
        this.oldAddressParameterOrder = oldAddressParameterOrder;
        this.newSizeParameterOrder = newSizeParameterOrder;
    }

}
