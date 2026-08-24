FN:{{ my_upper($name) }};
DIR:@datetime($ts);
COND:@admin($role)IS_ADMIN@else()NOT_ADMIN@endadmin;
ROUTE:@route('user.profile');
