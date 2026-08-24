LIST:@foreach ($list as $row){{ $row }},@endforeach
PAGER:{{ $list->links('pagination-demo') }}
