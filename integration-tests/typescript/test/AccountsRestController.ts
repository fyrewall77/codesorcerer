import {Injectable} from 'injection-js';
import * as qwest from 'qwest';
import {StompClient} from '@c1/stomp-client';
import {Subject} from 'rxjs';
import {plainToClass} from 'class-transformer';

import {Account} from './Account';  //Same package???
import {Observable} from './rxjs';  //No common prefix - use loc from Annotation
import {Single} from './rxjs';  //No common prefix - use loc from Annotation


@Injectable()
export class AccountsRestControllerService {
//-----------------Constructor
     constructor( private stompClient: StompClient ) {}

//-----------------Stomp Methods

public accounts(): Observable<Array<Account>> {
    return this.stompClient.topic('/queue/accounts')
       .map(x => plainToClass(Account, x));
}

//-----------------Rest Methods

public addAccount( body : Account) : Single<boolean> {
   let o = new Subject<boolean>();
   qwest.post( '/api/accounts/', body )
       .then((xhr, response) => {
            let x : boolean = plainToClass(boolean, response);
            o.next(x);
        })
       .catch((e, xhr, response)  => {
            o.error(e);
        });
   return o;
}
}