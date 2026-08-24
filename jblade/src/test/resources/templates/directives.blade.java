{{-- 这是注释，不应出现在输出中 SECRET_COMMENT --}}
GRADE:@if($score >= 90)A@elseif($score >= 60)B@else()C@endif;
@unless($isAdmin)NOT_ADMIN@endunless;
@isset($definedVar)ISSET_OK@endisset;
@empty($emptyList)EMPTY_OK@endempty;
LOOP:@foreach($items as $item){{ $loop->iteration }}={{ $item }}@if(!$loop->last),@endif@endforeach;
FORELSE:@forelse($none as $n)X@empty()NO_ITEMS@endforelse;
FOR:@for($i = 0; $i < 3; $i++)[{{ $i }}]@endfor;
@php
$x = 10;
$x = $x + 5;
@endphp
X={{ $x }};
VERB:@verbatim{{ raw }}@endverbatim;
AT:@@literal;
RAW:{!! $rawHtml !!};
ESC:{{ $rawHtml }};
INC:@include('partial', ['msg' => 'FROM_PARENT']);
